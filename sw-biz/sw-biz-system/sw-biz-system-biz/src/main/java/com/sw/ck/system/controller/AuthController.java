package com.sw.ck.system.controller;

import com.sw.ck.common.exception.BaseException;
import com.sw.ck.common.response.R;
import com.sw.ck.security.cache.LoginUserLoader;
import com.sw.ck.security.holder.LoginUser;
import com.sw.ck.security.holder.LoginUserHolder;
import com.sw.ck.security.jwt.JwtProperties;
import com.sw.ck.security.jwt.JwtTokenProvider;
import com.sw.ck.security.spi.UserDetailsProvider;
import com.sw.ck.system.entity.SysUser;
import com.sw.ck.system.model.TokenResponse;
import com.sw.ck.system.service.RefreshTokenService;
import com.sw.ck.system.service.SysUserService;
import com.sw.ck.system.util.CookieUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器：登录、刷新 token、登出。
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserDetailsProvider userDetailsProvider;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final SysUserService sysUserService;
    private final JwtProperties jwtProperties;
    private final RefreshTokenService refreshTokenService;
    private final LoginUserLoader loginUserLoader;

    @Value("${sw.security.cookie.secure:false}")
    private boolean cookieSecure;

    public AuthController(UserDetailsProvider userDetailsProvider,
                          PasswordEncoder passwordEncoder,
                          JwtTokenProvider jwtTokenProvider,
                          SysUserService sysUserService,
                          JwtProperties jwtProperties,
                          RefreshTokenService refreshTokenService,
                          LoginUserLoader loginUserLoader) {
        this.userDetailsProvider = userDetailsProvider;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.sysUserService = sysUserService;
        this.jwtProperties = jwtProperties;
        this.refreshTokenService = refreshTokenService;
        this.loginUserLoader = loginUserLoader;
    }

    /**
     * 账号密码登录。
     * <p>
     * 登录成功后同时下发 access token（响应体 data.accessToken）和
     * httpOnly refresh cookie（Set-Cookie: rt=...），前端按双 token 模式管理。
     *
     * @param request  登录请求（username + password）
     * @param response HTTP 响应（用于设置 refresh cookie）
     * @return 登录成功返回含 TokenResponse 的 R
     */
    @PostMapping("/login")
    public R<TokenResponse> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletResponse response) {
        log.info("用户登录: {}", request.getUsername());

        // 1. 根据用户名查询用户
        SysUser user = sysUserService.getByUsername(request.getUsername());
        if (user == null) {
            return R.fail(401, "用户名或密码错误");
        }

        // 2. 校验密码（用 BCrypt 匹配）
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return R.fail(401, "用户名或密码错误");
        }

        // 3. 校验账号状态（0=正常 1=停用 2=锁定；null/未知值按停用处理，拒绝签发 token）
        String statusDenyMessage = statusDenyMessage(user.getStatus());
        if (statusDenyMessage != null) {
            log.warn("用户 {} 登录被拒绝: {}", request.getUsername(), statusDenyMessage);
            return R.fail(401, statusDenyMessage);
        }

        // 4. 签发 access token
        String accessToken = jwtTokenProvider.generateToken(user.getId());

        // 5. 生成 refresh token → 写 DB + 设 httpOnly cookie
        String refreshToken = refreshTokenService.createRefreshToken(
                user.getId(), user.getTenantId(), jwtProperties.getRefreshExpireSeconds());
        CookieUtils.setRefreshCookie(response, refreshToken,
                (int) jwtProperties.getRefreshExpireSeconds(), cookieSecure);

        log.info("用户 {} 登录成功, userId={}", request.getUsername(), user.getId());
        return R.ok(new TokenResponse(accessToken, jwtProperties.getAccessExpireSeconds()));
    }

    /**
     * 刷新 access token（使用 refresh token cookie）。
     * <p>
     * 校验 refresh cookie → 轮换（旧撤销 + 新签发）→ 下发新 refresh cookie + 新 access token。
     * 检测到重放时撤销该用户全部会话。
     */
    @PostMapping("/refresh")
    public R<TokenResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        // 1. 从 cookie 读取 refresh token
        String rawToken = CookieUtils.getRefreshTokenFromCookie(request);
        if (rawToken == null || rawToken.isEmpty()) {
            return R.fail(401, "未提供 refresh token");
        }
        // 2. 校验 + 轮换
        try {
            RefreshTokenService.RefreshTokenRotation rotation =
                    refreshTokenService.rotateRefreshToken(rawToken, jwtProperties.getRefreshExpireSeconds());
            // 3. 重载用户并校验账号状态（停用/锁定/已删除账号不得续期：
            //    撤销刚轮换出的新 refresh token + 清除 cookie）
            SysUser user = sysUserService.getById(rotation.userId());
            String statusDenyMessage = user == null ? "账号已停用" : statusDenyMessage(user.getStatus());
            if (statusDenyMessage != null) {
                refreshTokenService.revokeRefreshToken(rotation.newRawToken());
                CookieUtils.clearRefreshCookie(response);
                log.warn("用户 {} refresh 被拒绝: {}", rotation.userId(), statusDenyMessage);
                return R.fail(401, statusDenyMessage);
            }
            // 4. 下发新 refresh cookie
            CookieUtils.setRefreshCookie(response, rotation.newRawToken(),
                    (int) jwtProperties.getRefreshExpireSeconds(), cookieSecure);
            // 5. 签发新 access token
            String newAccessToken = jwtTokenProvider.generateToken(rotation.userId());
            // 6. 返回
            return R.ok(new TokenResponse(newAccessToken, jwtProperties.getAccessExpireSeconds()));
        } catch (BaseException e) {
            // 轮换失败（无效/过期/重放）→ 清 cookie
            CookieUtils.clearRefreshCookie(response);
            return R.fail(e.getCode(), e.getMessage());
        }
    }

    /**
     * 登出（撤销 refresh token + 清除 cookie + 踢出缓存）。
     * <p>
     * 从 cookie 读取 refresh token → 撤销 → 清 cookie → 踢缓存。
     * 无 cookie / token 无效时静默成功（幂等）。
     */
    @PostMapping("/logout")
    public R<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        // 1. 从 cookie 读取 refresh token
        String rawToken = CookieUtils.getRefreshTokenFromCookie(request);
        // 2. 撤销 refresh token（幂等：无 token 时静默成功）
        refreshTokenService.revokeRefreshToken(rawToken);
        // 3. 清除 cookie
        CookieUtils.clearRefreshCookie(response);
        // 4. 如果有当前登录用户，踢出缓存
        try {
            LoginUser currentUser = LoginUserHolder.get();
            if (currentUser != null) {
                loginUserLoader.kickOut(currentUser.getUserId());
            }
        } catch (Exception ignored) {
            // 用户可能未认证（access token 已过期），忽略
        }
        return R.ok();
    }

    /**
     * 账号状态校验提示。
     * <p>
     * status=0（正常）→ 返回 null（放行）；status=2（锁定）→ "账号已锁定"；
     * 其余（1=停用，以及 null/未知值）→ "账号已停用"。登录与刷新两条链路共用，
     * 保证提示语义一致。
     *
     * @param status 用户状态
     * @return 拒绝提示语；正常状态返回 null
     */
    private String statusDenyMessage(Integer status) {
        if (status == null || status != 0) {
            return status != null && status == 2 ? "账号已锁定" : "账号已停用";
        }
        return null;
    }

    @Data
    public static class LoginRequest {

        @NotBlank(message = "用户名不能为空")
        private String username;

        @NotBlank(message = "密码不能为空")
        private String password;
    }
}
