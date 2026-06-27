package com.sw.ck.security.filter;

import com.sw.ck.security.cache.LoginUserLoader;
import com.sw.ck.security.config.SecurityProperties;
import com.sw.ck.security.holder.LoginUser;
import com.sw.ck.security.holder.LoginUserHolder;
import com.sw.ck.security.jwt.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 从请求头解析 JWT → 校验 → 取出 userId → 经 {@link LoginUserLoader}（缓存优先，未命中
 * 才回查 {@code UserDetailsProvider} 并写回缓存）装载完整 {@link LoginUser} → 同时注入
 * {@link SecurityContextHolder}（供 {@code authorizeHttpRequests}/{@code @PreAuthorize}
 * 判断"已认证"）与 {@link LoginUserHolder}（供 {@code @ss.hasPermi}、MetaObjectHandler 等
 * 直接取用）。请求处理完毕统一在 finally 中 clear 两者，避免容器线程池复用造成上下文泄漏。
 * <p>
 * Authentication 的 authorities 固定为空集合：本项目的权限判断走 {@code @ss.hasPermi(...)}
 * 直接读取 {@link LoginUser#getPermissions()}，不使用 Spring Security 的
 * GrantedAuthority/hasAuthority 体系，避免维护两套权限表示。
 * <p>
 * {@code loginUserLoader} 由 Spring 托管注入、永不为 null（{@code SecurityAutoConfiguration}
 * 已改为无条件创建并经 {@code ObjectProvider} 运行期解析 {@code UserDetailsProvider}），因此
 * 本过滤器不再有「loader 为 null 则整段跳过」的早退逻辑——那曾把「安全链未装配」这一启动期
 * 缺陷静默降级为对所有请求 401，掩盖了根因。
 * <p>
 * 异常分档（CLAUDE.md §8：令牌失败=401 vs 基础设施/装配故障=500/503，不得统一降级）：
 * 仅 token 自身的解析/校验失败在此 catch 并降级为「未认证」，交由 authorizeHttpRequests +
 * {@code AuthenticationEntryPoint} 统一吐 401；而 {@code loadByUserId} 内部因
 * {@code UserDetailsProvider} 缺失（装配故障）或 Redis/回查异常（基础设施故障）抛出的异常
 * 【不在此 catch】，任其向上传播渲染为 5xx，绝不伪装成 401。
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final LoginUserLoader loginUserLoader;
    private final SecurityProperties securityProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            authenticate(request);
            filterChain.doFilter(request, response);
        } finally {
            LoginUserHolder.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(HttpServletRequest request) {
        String token = resolveToken(request);
        if (token == null) {
            return;
        }
        Long userId;
        try {
            if (!jwtTokenProvider.validate(token)) {
                return;
            }
            userId = jwtTokenProvider.parseUserId(token);
        } catch (Exception e) {
            // 仅 token 自身的解析/校验失败 → 视为未认证，交由 AuthenticationEntryPoint 统一吐 401。
            log.warn("JWT 认证失败: {}", e.getMessage());
            return;
        }
        // loadByUserId 内部若因 UserDetailsProvider 缺失（装配故障）或 Redis/回查异常（基础设施
        // 故障）抛出，【刻意不在此 catch】——任其向上传播渲染为 5xx，绝不降级为 401（CLAUDE.md §8）。
        LoginUser loginUser = loginUserLoader.loadByUserId(userId);
        if (loginUser == null) {
            return;
        }
        LoginUserHolder.set(loginUser);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(loginUser, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(securityProperties.getTokenHeader());
        if (header != null && header.startsWith(securityProperties.getTokenPrefix())) {
            return header.substring(securityProperties.getTokenPrefix().length());
        }
        return null;
    }
}
