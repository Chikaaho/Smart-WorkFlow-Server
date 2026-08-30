package com.sw.ck.system.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 登录 / 刷新 token 响应 DTO。
 * 包含 JWT access token 及其过期时间（秒），前端据此计算刷新时机。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse implements Serializable {

    /** JWT access token，前端内存存储 */
    private String accessToken;

    /** access token 过期时间（秒），前端据此计算刷新时机 */
    private long expiresIn;
}
