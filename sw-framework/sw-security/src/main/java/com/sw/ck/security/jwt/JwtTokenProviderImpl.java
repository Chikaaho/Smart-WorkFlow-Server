package com.sw.ck.security.jwt;

import com.sw.ck.common.exception.BaseException;
import com.sw.ck.common.exception.CommonErrorCode;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * 基于 jjwt 的默认实现：token 只携带 userId（subject）与签发/过期时间，不下放
 * deptId/dataScope/permissions 等会变化的信息——理由见
 * {@link com.sw.ck.security.spi.UserDetailsProvider} 类注释：认证后由
 * {@code LoginUserLoader} 回查/缓存完整 {@code LoginUser}，token 本身保持最小化、
 * 不需要因权限变更而作废。
 */
@RequiredArgsConstructor
public class JwtTokenProviderImpl implements JwtTokenProvider {

    private final JwtProperties jwtProperties;

    @Override
    public String generateToken(Long userId) {
        Date now = new Date();
        long expireSeconds = jwtProperties.getAccessExpireSeconds() > 0
                ? jwtProperties.getAccessExpireSeconds()
                : jwtProperties.getExpireSeconds();
        Date expiration = new Date(now.getTime() + expireSeconds * 1000);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey())
                .compact();
    }

    @Override
    public Long parseUserId(String token) {
        try {
            String subject = Jwts.parser()
                    .verifyWith(secretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return Long.valueOf(subject);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BaseException(CommonErrorCode.UNAUTHORIZED, "token 无效或已过期");
        }
    }

    @Override
    public boolean validate(String token) {
        try {
            Jwts.parser().verifyWith(secretKey()).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
