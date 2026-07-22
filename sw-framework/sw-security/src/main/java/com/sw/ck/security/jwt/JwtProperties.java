package com.sw.ck.security.jwt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "sw.security.jwt")
public class JwtProperties {

    private String secret;
    private long expireSeconds = 7200;

    /**
     * Access Token 过期时间（秒），默认 15 分钟。
     * JwtTokenProviderImpl.generateToken() 优先使用此值；若为 0 则回退到 {@link #expireSeconds}。
     */
    private long accessExpireSeconds = 900;

    /**
     * Refresh Token 过期时间（秒），默认 7 天。
     * 用于设置 sys_refresh_token.expires_at = now + refreshExpireSeconds。
     */
    private long refreshExpireSeconds = 604800;
}
