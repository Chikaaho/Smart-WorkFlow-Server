package com.sw.ck.security.jwt;

public interface JwtTokenProvider {

    String generateToken(Long userId);

    Long parseUserId(String token);

    boolean validate(String token);
}
