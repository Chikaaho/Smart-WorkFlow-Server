package com.sw.ck.security.jwt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "sw.security.jwt")
public class JwtProperties {

    private String secret;
    private long expireSeconds = 7200;
}
