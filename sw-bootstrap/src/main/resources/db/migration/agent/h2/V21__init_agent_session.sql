-- ===================================================================
-- Smart-WorkFlow :: V21: 初始化 Agent 会话主表 (H2)
-- M07 Step4 F04 对话交互：会话主表
-- PK = 雪花 ID（Java 层生成，MyBatis-Plus IdType.ASSIGN_ID）
-- status varchar(20) 对齐 sw_bpm_instance 惯例（V8）
-- create_time 无 DEFAULT：由 Java 层（MetaObjectHandler）显式赋值（agent 模块 V19/V20 惯例）
-- ===================================================================
CREATE TABLE sw_agent_session (
    id                    BIGINT      NOT NULL PRIMARY KEY,
    agent_model_config_id BIGINT      NOT NULL,
    title                 VARCHAR(500),
    status                VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    create_time           TIMESTAMP   NOT NULL,
    create_by             VARCHAR(64),
    update_time           TIMESTAMP,
    update_by             VARCHAR(64),
    deleted               SMALLINT    NOT NULL DEFAULT 0,
    tenant_id             BIGINT      NOT NULL DEFAULT 0,
    version               BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_sw_agent_session_user ON sw_agent_session (tenant_id, create_by, deleted);
CREATE INDEX idx_sw_agent_session_cfg  ON sw_agent_session (agent_model_config_id, deleted);
