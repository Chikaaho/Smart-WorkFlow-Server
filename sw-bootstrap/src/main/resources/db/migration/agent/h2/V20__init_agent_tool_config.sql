-- ===================================================================
-- Smart-WorkFlow :: V20: 初始化工具沙箱白名单表 (H2)
-- M07 Step3 工具沙箱：内部工具（Spring bean 反射调用）+ 外部工具（HTTP 调用）
-- 安全边界：工具名 → (beanName, methodName) / (url, httpMethod) 映射仅存于
-- 白名单表，管理员写入，LLM/用户不可在运行时新增条目
-- input_schema（JSON Schema 字符串，可能较长）：H2 用 CLOB（参照 V19 api_key_cipher 惯例）
-- ===================================================================
CREATE TABLE sw_agent_tool_internal (
    id              BIGINT NOT NULL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(500) NOT NULL,
    input_schema    CLOB,
    bean_name       VARCHAR(100) NOT NULL,
    method_name     VARCHAR(100) NOT NULL,
    enabled         SMALLINT NOT NULL DEFAULT 1,
    remark          VARCHAR(500),
    create_time     TIMESTAMP,
    create_by       VARCHAR(64),
    update_time     TIMESTAMP,
    update_by       VARCHAR(64),
    deleted         SMALLINT NOT NULL DEFAULT 0,
    tenant_id       BIGINT NOT NULL DEFAULT 0,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE sw_agent_tool_external (
    id              BIGINT NOT NULL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(500) NOT NULL,
    input_schema    CLOB,
    url             VARCHAR(500) NOT NULL,
    http_method     VARCHAR(10) NOT NULL DEFAULT 'POST',
    timeout_seconds INT NOT NULL DEFAULT 30,
    enabled         SMALLINT NOT NULL DEFAULT 1,
    remark          VARCHAR(500),
    create_time     TIMESTAMP,
    create_by       VARCHAR(64),
    update_time     TIMESTAMP,
    update_by       VARCHAR(64),
    deleted         SMALLINT NOT NULL DEFAULT 0,
    tenant_id       BIGINT NOT NULL DEFAULT 0,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_sw_agent_tool_internal_tenant_deleted ON sw_agent_tool_internal (tenant_id, deleted);
CREATE INDEX idx_sw_agent_tool_external_tenant_deleted ON sw_agent_tool_external (tenant_id, deleted);
