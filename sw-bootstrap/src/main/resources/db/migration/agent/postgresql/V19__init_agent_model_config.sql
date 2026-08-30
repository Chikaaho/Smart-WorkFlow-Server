-- ===================================================================
-- Smart-WorkFlow :: V19: 初始化大模型接入配置表 (PostgreSQL)
-- M07-F01 大模型管理：API Key 以 AesGcmCipher 密文（TEXT）落库
-- ===================================================================
CREATE TABLE sw_agent_model_config (
    id              BIGINT NOT NULL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    protocol_type   VARCHAR(32) NOT NULL,
    base_url        VARCHAR(500) NOT NULL,
    model_name      VARCHAR(100) NOT NULL,
    api_key_cipher  TEXT,
    temperature     DECIMAL(4,2),
    max_tokens      INT,
    top_p           DECIMAL(4,2),
    timeout_seconds INT NOT NULL DEFAULT 30,
    retry_count     INT NOT NULL DEFAULT 0,
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

CREATE UNIQUE INDEX uk_sw_agent_model_name ON sw_agent_model_config (tenant_id, name);
CREATE INDEX idx_sw_agent_model_tenant_deleted ON sw_agent_model_config (tenant_id, deleted);

COMMENT ON TABLE sw_agent_model_config IS 'M07 大模型接入配置';
COMMENT ON COLUMN sw_agent_model_config.protocol_type IS '协议类型：openai/ollama/other';
COMMENT ON COLUMN sw_agent_model_config.api_key_cipher IS 'API Key 密文（AesGcmCipher）';
