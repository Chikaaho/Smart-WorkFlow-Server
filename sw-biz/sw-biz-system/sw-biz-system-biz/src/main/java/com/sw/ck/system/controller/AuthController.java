package com.sw.ck.system.controller;

import com.sw.ck.common.response.R;
import com.sw.ck.security.holder.LoginUser;
import com.sw.ck.security.jwt.JwtTokenProvider;
import com.sw.ck.security.spi.UserDetailsProvider;
import com.sw.ck.system.entity.SysUser;
import com.sw.ck.system.service.SysUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器：登录。
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserDetailsProvider userDetailsProvider;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final SysUserService sysUserService;

    public AuthController(UserDetailsProvider userDetailsProvider,
                          PasswordEncoder passwordEncoder,
                          JwtTokenProvider jwtTokenProvider,
                          SysUserService sysUserService) {
        this.userDetailsProvider = userDetailsProvider;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.sysUserService = sysUserService;
    }

    /**
     * 账号密码登录。
     *
     * @param request 登录请求（username + password）
     * @return 登录成功返回含 token 的 R
     */
    @PostMapping("/login")
    public R<String> login(@Valid @RequestBody LoginRequest request) {
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

        // 3. 签发 token
        String token = jwtTokenProvider.generateToken(user.getId());
        log.info("用户 {} 登录成功, userId={}", request.getUsername(), user.getId());
        return R.ok(token);
    }

    @Data
    public static class LoginRequest {

        @NotBlank(message = "用户名不能为空")
        private String username;

        @NotBlank(message = "密码不能为空")
        private String password;
    }
}
