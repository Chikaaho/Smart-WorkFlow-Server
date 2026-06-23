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
import org.springframework.lang.Nullable;
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
 * {@code loginUserLoader} 为 null 时（当前仓库还没有任何 {@code UserDetailsProvider} 实现，
 * 该 Bean 处于未装载状态）本过滤器不做任何认证动作、直接放行：未认证请求会在
 * {@code authorizeHttpRequests} 阶段被拦截，仅 {@code sw.security.permit-urls} 白名单可访问，
 * 这就是 Prompt 要求的"临时放行，仅为验证启动"的效果，不需要额外 mock 实现。
 * <p>
 * {@code authenticate} 内部统一 catch 异常并降级为"未认证"：本过滤器跑在 DispatcherServlet
 * 之前，不经过 {@code GlobalExceptionHandler}，token 解析失败或 Redis/UserDetailsProvider
 * 回查异常如果不在这里捕获，会被容器渲染成裸 500 页面，而不是统一的 R 结构 401/403——这与
 * Prompt 要求的"统一认证/鉴权异常走 R 返回"相悖，因此在此降级处理，交由后续的
 * authorizeHttpRequests + AuthenticationEntryPoint 统一吐出 401。
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    @Nullable
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
        if (loginUserLoader == null) {
            return;
        }
        String token = resolveToken(request);
        if (token == null) {
            return;
        }
        try {
            if (!jwtTokenProvider.validate(token)) {
                return;
            }
            Long userId = jwtTokenProvider.parseUserId(token);
            LoginUser loginUser = loginUserLoader.loadByUserId(userId);
            if (loginUser == null) {
                return;
            }
            LoginUserHolder.set(loginUser);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(loginUser, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception e) {
            log.warn("JWT 认证失败: {}", e.getMessage());
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(securityProperties.getTokenHeader());
        if (header != null && header.startsWith(securityProperties.getTokenPrefix())) {
            return header.substring(securityProperties.getTokenPrefix().length());
        }
        return null;
    }
}
