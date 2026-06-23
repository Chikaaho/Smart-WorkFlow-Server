package com.sw.ck.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "sw.security")
public class SecurityProperties {

    private String tokenHeader = "Authorization";
    private String tokenPrefix = "Bearer ";

    /**
     * 免认证白名单（Ant 风格路径），登录/验证码/swagger/openapi 等接口需加入此列表。
     * 业务方可通过 sw.security.permit-urls 整体覆盖；此处为最小可用的默认值。
     */
    private List<String> permitUrls = new ArrayList<>(List.of(
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/doc.html", "/webjars/**",
            "/actuator/**"
    ));
}
