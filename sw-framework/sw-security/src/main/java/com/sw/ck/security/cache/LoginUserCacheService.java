package com.sw.ck.security.cache;

import com.sw.ck.security.holder.LoginUser;
import com.sw.ck.security.jwt.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * LoginUser 的 Redis 缓存读写，key 以 userId 维度组织。
 * <p>
 * TTL 与 JWT 过期时间保持一致：token 仍在有效期内时，缓存也应同时失效，强制下一次请求
 * 重新走 {@link com.sw.ck.security.spi.UserDetailsProvider} 回查，避免长期持有陈旧权限。
 * {@link #evict} 额外用于主动踢人下线/权限变更立即生效的场景。
 */
@RequiredArgsConstructor
public class LoginUserCacheService {

    private static final String KEY_PREFIX = "sw:security:login-user:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtProperties jwtProperties;

    public void cache(LoginUser loginUser) {
        long ttlSeconds = jwtProperties.getAccessExpireSeconds() > 0
                ? jwtProperties.getAccessExpireSeconds()
                : jwtProperties.getExpireSeconds();
        redisTemplate.opsForValue().set(buildKey(loginUser.getUserId()), loginUser,
                ttlSeconds, TimeUnit.SECONDS);
    }

    public LoginUser get(Long userId) {
        return (LoginUser) redisTemplate.opsForValue().get(buildKey(userId));
    }

    public void evict(Long userId) {
        redisTemplate.delete(buildKey(userId));
    }

    private String buildKey(Long userId) {
        return KEY_PREFIX + userId;
    }
}
