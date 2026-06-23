package com.sw.ck.security.config;

import com.sw.ck.common.config.mybatis.MybatisPlusConfig;
import com.sw.ck.common.security.LoginContextProvider;
import com.sw.ck.security.cache.LoginUserCacheService;
import com.sw.ck.security.cache.LoginUserLoader;
import com.sw.ck.security.jwt.JwtProperties;
import com.sw.ck.security.jwt.JwtTokenProvider;
import com.sw.ck.security.jwt.JwtTokenProviderImpl;
import com.sw.ck.security.spi.UserDetailsProvider;
import com.sw.ck.security.support.SecurityLoginContextProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * before = MybatisPlusConfig.class：确保本类的 LoginContextProvider 先于
 * sw-common 的兜底默认实现注册，使 @ConditionalOnMissingBean 生效、降级实现不会抢先装载。
 * sw-common 不能反向依赖 sw-security，故此排序只能在依赖方向合法的这一侧声明。
 */
@AutoConfiguration(before = MybatisPlusConfig.class)
@EnableConfigurationProperties({SecurityProperties.class, JwtProperties.class})
public class SecurityAutoConfiguration {

    @Bean
    public LoginContextProvider securityLoginContextProvider() {
        return new SecurityLoginContextProvider();
    }

    /**
     * 默认 JWT 实现，业务方可注册同类型自定义 Bean 覆盖（@ConditionalOnMissingBean）。
     */
    @Bean
    @ConditionalOnMissingBean(JwtTokenProvider.class)
    public JwtTokenProvider jwtTokenProvider(JwtProperties jwtProperties) {
        return new JwtTokenProviderImpl(jwtProperties);
    }

    /**
     * 仅在 UserDetailsProvider 已被某个业务模块（sw-module-system）实现并注册为 Bean 时才装载
     * 装载+缓存骨架；当前仓库还没有任何实现，这两个 Bean 暂不会被创建，属于预期行为。
     */
    @Bean
    @ConditionalOnBean(UserDetailsProvider.class)
    public LoginUserCacheService loginUserCacheService(RedisTemplate<String, Object> redisTemplate,
                                                         JwtProperties jwtProperties) {
        return new LoginUserCacheService(redisTemplate, jwtProperties);
    }

    @Bean
    @ConditionalOnBean(UserDetailsProvider.class)
    public LoginUserLoader loginUserLoader(UserDetailsProvider userDetailsProvider,
                                            LoginUserCacheService loginUserCacheService) {
        return new LoginUserLoader(userDetailsProvider, loginUserCacheService);
    }
}
