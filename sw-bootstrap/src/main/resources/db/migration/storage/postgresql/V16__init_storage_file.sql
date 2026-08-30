-- ===================================================================
-- Smart-WorkFlow :: V16: 初始化文件存储记录表 (PostgreSQL)
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

COMMENT ON TABLE  sw_storage_file              IS '文件存储记录';
COMMENT ON COLUMN sw_storage_file.id            IS '主键';
COMMENT ON COLUMN sw_storage_file.original_name IS '文件原始名称';
COMMENT ON COLUMN sw_storage_file.storage_key   IS '存储唯一标识（提供商侧 key）';
COMMENT ON COLUMN sw_storage_file.storage_name  IS '存储文件名（系统重命名，含扩展名）';
COMMENT ON COLUMN sw_storage_file.file_size     IS '文件大小（字节）';
COMMENT ON COLUMN sw_storage_file.content_type  IS '文件 MIME 类型';
COMMENT ON COLUMN sw_storage_file.file_ext      IS '文件扩展名（小写，不含点）';
COMMENT ON COLUMN sw_storage_file.provider_type IS '存储提供商类型（local/minio/cos/qiniu）';
COMMENT ON COLUMN sw_storage_file.bucket_name   IS '存储桶名称（本地模式为目录名）';
COMMENT ON COLUMN sw_storage_file.storage_url   IS '文件访问地址';
COMMENT ON COLUMN sw_storage_file.create_time   IS '创建时间';
COMMENT ON COLUMN sw_storage_file.create_by     IS '创建人';
COMMENT ON COLUMN sw_storage_file.update_time   IS '更新时间';
COMMENT ON COLUMN sw_storage_file.update_by     IS '更新人';
COMMENT ON COLUMN sw_storage_file.deleted       IS '逻辑删除标记（0=未删, 1=已删）';
COMMENT ON COLUMN sw_storage_file.tenant_id     IS '租户 ID';
COMMENT ON COLUMN sw_storage_file.version       IS '乐观锁版本号';

CREATE INDEX idx_sw_storage_file_tenant_deleted ON sw_storage_file (tenant_id, deleted);
