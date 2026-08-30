package com.sw.ck.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sw.ck.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Refresh Token 存储表实体。
 * 对应 sys_refresh_token 表，存不透明 refresh token 的 SHA-256 哈希值。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_refresh_token")
public class SysRefreshToken extends BaseEntity {

    /** 关联用户 ID（sys_user.id） */
    @TableField("user_id")
    private Long userId;

    /** Refresh Token 的 SHA-256 哈希值 */
    @TableField("token_hash")
    private String tokenHash;

    /** 过期时间 */
    @TableField("expires_at")
    private LocalDateTime expiresAt;

    /** 是否已撤销（0=有效, 1=已撤销） */
    @TableField("revoked")
    private Integer revoked;
}
