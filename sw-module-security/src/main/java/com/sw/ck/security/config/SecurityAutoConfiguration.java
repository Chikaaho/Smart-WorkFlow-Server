package com.sw.ck.security.config;

import com.sw.ck.security.jwt.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({SecurityProperties.class, JwtProperties.class})
public class SecurityAutoConfiguration {
}
