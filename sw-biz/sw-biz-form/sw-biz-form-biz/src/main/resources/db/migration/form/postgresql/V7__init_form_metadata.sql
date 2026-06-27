-- ===================================================================
-- Smart-WorkFlow :: Form 模块固定元数据表 (PostgreSQL)
-- ===================================================================
-- 注意：动态宽表（sw_form_{nanoId} / sw_form_table_{nanoId}）不在此处，
-- 由 DynamicTableManager 按 §6.2 例外管理。
-- ===================================================================

-- ==================== 1. 表单定义主表 ====================
CREATE TABLE sw_form_def (
    id                   VARCHAR(36)  PRIMARY KEY,
    form_key             VARCHAR(100) NOT NULL UNIQUE,
    name                 VARCHAR(200) NOT NULL,
    logical_table_name   VARCHAR(100),
    status               VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    physical_table_name  VARCHAR(100),
    form_version         INT          NOT NULL DEFAULT 1,
    description          VARCHAR(500),
    sub_table_mapping    TEXT,
    tenant_id            BIGINT       NOT NULL DEFAULT 0,
    deleted              SMALLINT     NOT NULL DEFAULT 0,
    create_time          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by            BIGINT,
    update_time          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by            BIGINT,
    version              BIGINT       NOT NULL DEFAULT 0
);

COMMENT ON TABLE  sw_form_def             IS '表单定义主表';
COMMENT ON COLUMN sw_form_def.id           IS 'UUID 主键';
COMMENT ON COLUMN sw_form_def.form_key     IS '表单业务标识（唯一）';
COMMENT ON COLUMN sw_form_def.name         IS '表单名称';
COMMENT ON COLUMN sw_form_def.logical_table_name IS '用户自定义逻辑表名';
COMMENT ON COLUMN sw_form_def.status       IS '状态: DRAFT(草稿) / PUBLISHED(已发布)';
COMMENT ON COLUMN sw_form_def.physical_table_name IS '发布后回填的动态宽表物理名';
COMMENT ON COLUMN sw_form_def.form_version IS '表单版本号（每次发布递增）';

-- ==================== 2. 表单配置/样式表 ====================
CREATE TABLE sw_form_config (
    id          VARCHAR(36)  PRIMARY KEY,
    form_id     VARCHAR(36)  NOT NULL,
    definition  JSONB        NOT NULL,
    tenant_id   BIGINT       NOT NULL DEFAULT 0,
    deleted     SMALLINT     NOT NULL DEFAULT 0,
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by   BIGINT,
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   BIGINT,
    version     BIGINT       NOT NULL DEFAULT 0
);

COMMENT ON TABLE  sw_form_config               IS '表单配置/样式表';
COMMENT ON COLUMN sw_form_config.id             IS 'UUID 主键';
COMMENT ON COLUMN sw_form_config.form_id        IS '关联 sw_form_def.id';
COMMENT ON COLUMN sw_form_config.definition     IS '表单样式/控件/布局 schema (JSONB)';

-- ==================== 3. 表单快照表 ====================
CREATE TABLE sw_form_snapshot (
    id           VARCHAR(36)  PRIMARY KEY,
    form_id      VARCHAR(36)  NOT NULL,
    form_version INT          NOT NULL,
    definition   JSONB        NOT NULL,
    tenant_id    BIGINT       NOT NULL DEFAULT 0,
    deleted      SMALLINT     NOT NULL DEFAULT 0,
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by    BIGINT,
    update_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    BIGINT,
    version      BIGINT       NOT NULL DEFAULT 0
);

COMMENT ON TABLE  sw_form_snapshot               IS '表单版本快照表';
COMMENT ON COLUMN sw_form_snapshot.id             IS 'UUID 主键';
COMMENT ON COLUMN sw_form_snapshot.form_id        IS '关联 sw_form_def.id';
COMMENT ON COLUMN sw_form_snapshot.form_version   IS '快照版本号（与 sw_form_def.form_version 对齐）';
COMMENT ON COLUMN sw_form_snapshot.definition     IS '该版本的完整 definition JSONB 快照';

-- ==================== 4. 表单提交溯源表 ====================
CREATE TABLE sw_form_trace (
    id                 VARCHAR(36)  PRIMARY KEY,
    form_id            VARCHAR(36)  NOT NULL,
    record_id          VARCHAR(36)  NOT NULL,
    submit_user_id     BIGINT       NOT NULL,
    submit_ip          VARCHAR(200),
    submit_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    device_fingerprint VARCHAR(200),
    user_agent         VARCHAR(500),
    tenant_id          BIGINT       NOT NULL DEFAULT 0,
    deleted            SMALLINT     NOT NULL DEFAULT 0,
    create_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by          BIGINT,
    update_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by          BIGINT,
    version            BIGINT       NOT NULL DEFAULT 0
);

COMMENT ON TABLE  sw_form_trace                   IS '表单提交溯源表';
COMMENT ON COLUMN sw_form_trace.id                 IS 'UUID 主键';
COMMENT ON COLUMN sw_form_trace.form_id            IS '关联 sw_form_def.id';
COMMENT ON COLUMN sw_form_trace.record_id          IS '动态宽表记录 UUID';
COMMENT ON COLUMN sw_form_trace.submit_user_id     IS '提交人（BIGINT，指向 sys_user）';
COMMENT ON COLUMN sw_form_trace.submit_ip          IS '提交 IP（AES 加密存储）';
COMMENT ON COLUMN sw_form_trace.device_fingerprint IS '设备指纹（哈希值）';
