-- ===================================================================
-- Smart-WorkFlow :: V18: Refresh Token 存储表 (H2)
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

CREATE INDEX idx_srt_user_tenant ON sys_refresh_token (user_id, tenant_id);
CREATE UNIQUE INDEX uk_srt_token_hash ON sys_refresh_token (token_hash);
