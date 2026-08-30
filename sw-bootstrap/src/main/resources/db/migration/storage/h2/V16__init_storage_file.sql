-- ===================================================================
-- Smart-WorkFlow :: V16: 初始化文件存储记录表 (H2)
-- ===================================================================
CREATE TABLE sw_storage_file (
    id              BIGINT      NOT NULL,
    original_name   VARCHAR(512) NOT NULL,
    storage_key     VARCHAR(512) NOT NULL,
    storage_name    VARCHAR(512) NOT NULL,
    file_size       BIGINT      NOT NULL DEFAULT 0,
    content_type    VARCHAR(255) DEFAULT NULL,
    file_ext        VARCHAR(32)  DEFAULT NULL,
    provider_type   VARCHAR(32)  NOT NULL DEFAULT 'local',
    bucket_name     VARCHAR(255) DEFAULT NULL,
    storage_url     VARCHAR(1024) DEFAULT NULL,
    create_time     TIMESTAMP    DEFAULT NULL,
    create_by       BIGINT       DEFAULT NULL,
    update_time     TIMESTAMP    DEFAULT NULL,
    update_by       BIGINT       DEFAULT NULL,
    deleted         SMALLINT     NOT NULL DEFAULT 0,
    tenant_id       BIGINT       NOT NULL DEFAULT 0,
    version         BIGINT       DEFAULT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_sw_storage_file_tenant_deleted ON sw_storage_file (tenant_id, deleted);
