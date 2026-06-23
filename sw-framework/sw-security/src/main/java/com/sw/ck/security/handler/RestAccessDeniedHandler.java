package com.sw.ck.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw.ck.common.exception.CommonErrorCode;
import com.sw.ck.common.response.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * 已认证但无权限（含 {@code @PreAuthorize} 鉴权失败）统一返回 403 + {@link R} 结构。
 */
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        JsonResponseWriter.write(response, objectMapper, HttpServletResponse.SC_FORBIDDEN,
                R.fail(CommonErrorCode.FORBIDDEN.getCode(), CommonErrorCode.FORBIDDEN.getMessage()));
    }
}
