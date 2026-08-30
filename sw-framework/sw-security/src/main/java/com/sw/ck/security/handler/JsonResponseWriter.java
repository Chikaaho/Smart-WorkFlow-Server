package com.sw.ck.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw.ck.common.response.R;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;

/**
 * {@link RestAuthenticationEntryPoint}/{@link RestAccessDeniedHandler} 共用的 JSON 写出逻辑，
 * 保证认证/鉴权失败时的响应体格式与 {@code GlobalExceptionHandler} 一致。
 */
final class JsonResponseWriter {

    private JsonResponseWriter() {
    }

    static void write(HttpServletResponse response, ObjectMapper objectMapper, int status, R<?> body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
