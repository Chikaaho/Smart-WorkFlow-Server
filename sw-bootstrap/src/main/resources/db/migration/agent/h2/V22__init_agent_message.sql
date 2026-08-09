-- ===================================================================
-- Smart-WorkFlow :: V22: 初始化 Agent 会话消息明细表 (H2)
-- M07 Step4 F04 对话交互：会话内消息明细
-- role：'USER' / 'ASSISTANT' / 'SYSTEM'（varchar + String，仓库惯例，不建 enum）
-- msg_order：本会话内消息顺序号（0-based，写入时由 Java 层计算 = 已有消息数）
-- content：大文本，H2 用 CLOB（agent 模块 V19 api_key_cipher 惯例）
-- ===================================================================
CREATE TABLE sw_agent_message (
    id          BIGINT      NOT NULL PRIMARY KEY,
    session_id  BIGINT      NOT NULL,
    role        VARCHAR(20) NOT NULL,
    content     CLOB        NOT NULL,
    msg_order   INT         NOT NULL,
    create_time TIMESTAMP   NOT NULL,
    create_by   VARCHAR(64),
    update_time TIMESTAMP,
    update_by   VARCHAR(64),
    deleted     SMALLINT    NOT NULL DEFAULT 0,
    tenant_id   BIGINT      NOT NULL DEFAULT 0,
    version     BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_sw_agent_msg_session ON sw_agent_message (session_id, msg_order, deleted);
