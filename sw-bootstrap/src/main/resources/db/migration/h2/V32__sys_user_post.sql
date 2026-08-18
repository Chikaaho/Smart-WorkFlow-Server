CREATE TABLE sys_user_post (
    id BIGINT NOT NULL,
    create_time TIMESTAMP,
    create_by BIGINT,
    update_time TIMESTAMP,
    update_by BIGINT,
    deleted SMALLINT NOT NULL DEFAULT 0,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL
);
CREATE UNIQUE INDEX uk_sys_user_post ON sys_user_post (tenant_id, user_id, post_id, deleted);
CREATE INDEX idx_sys_user_post_user ON sys_user_post (tenant_id, user_id, deleted);
CREATE INDEX idx_sys_user_post_post ON sys_user_post (tenant_id, post_id, deleted);
