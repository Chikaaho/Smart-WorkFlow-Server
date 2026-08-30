package com.sw.ck.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw.ck.common.exception.CommonErrorCode;
import com.sw.ck.common.response.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * 未认证（缺 token / token 失效）统一返回 401 + {@link R} 结构，替代 Spring Security
 * 默认的跳转登录页行为。
 */
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        JsonResponseWriter.write(response, objectMapper, HttpServletResponse.SC_UNAUTHORIZED,
                R.fail(CommonErrorCode.UNAUTHORIZED.getCode(), CommonErrorCode.UNAUTHORIZED.getMessage()));
    }
}
