-- ===================================================================
-- Smart-WorkFlow :: V18: Refresh Token 存储表 (PostgreSQL)
-- ===================================================================
CREATE TABLE sys_refresh_token (
    id          BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    token_hash  VARCHAR(128) NOT NULL,
    expires_at  TIMESTAMP    NOT NULL,
    revoked     SMALLINT     NOT NULL DEFAULT 0,
    create_time TIMESTAMP    DEFAULT NULL,
    create_by   BIGINT       DEFAULT NULL,
    update_time TIMESTAMP    DEFAULT NULL,
    update_by   BIGINT       DEFAULT NULL,
    deleted     SMALLINT     NOT NULL DEFAULT 0,
    tenant_id   BIGINT       NOT NULL DEFAULT 0,
    version     BIGINT       DEFAULT NULL,
    PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_refresh_token             IS 'Refresh Token 存储表';
COMMENT ON COLUMN sys_refresh_token.id          IS '主键';
COMMENT ON COLUMN sys_refresh_token.user_id     IS '关联用户 ID（sys_user.id）';
COMMENT ON COLUMN sys_refresh_token.token_hash  IS 'Refresh Token 的 SHA-256 哈希值';
COMMENT ON COLUMN sys_refresh_token.expires_at  IS '过期时间';
COMMENT ON COLUMN sys_refresh_token.revoked     IS '是否已撤销（0=有效, 1=已撤销）';
COMMENT ON COLUMN sys_refresh_token.create_time IS '创建时间';
COMMENT ON COLUMN sys_refresh_token.create_by   IS '创建人';
COMMENT ON COLUMN sys_refresh_token.update_time IS '更新时间';
COMMENT ON COLUMN sys_refresh_token.update_by   IS '更新人';
COMMENT ON COLUMN sys_refresh_token.deleted     IS '逻辑删除标记（0=未删, 1=已删）';
COMMENT ON COLUMN sys_refresh_token.tenant_id   IS '租户 ID';
COMMENT ON COLUMN sys_refresh_token.version     IS '乐观锁版本号';

CREATE INDEX idx_srt_user_tenant ON sys_refresh_token (user_id, tenant_id);
CREATE UNIQUE INDEX uk_srt_token_hash ON sys_refresh_token (token_hash);
