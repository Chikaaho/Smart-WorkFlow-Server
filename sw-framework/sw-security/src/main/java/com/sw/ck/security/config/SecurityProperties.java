package com.sw.ck.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "sw.security")
public class SecurityProperties {

    private String tokenHeader = "Authorization";
    private String tokenPrefix = "Bearer ";
}
