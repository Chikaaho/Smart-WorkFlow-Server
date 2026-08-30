-- ===================================================================
-- Smart-WorkFlow :: V22: 初始化 Agent 会话消息明细表 (PostgreSQL)
-- M07 Step4 F04 对话交互：会话内消息明细
-- role：'USER' / 'ASSISTANT' / 'SYSTEM'（varchar + String，仓库惯例，不建 enum）
-- msg_order：本会话内消息顺序号（0-based，写入时由 Java 层计算 = 已有消息数）
-- content：大文本，PG 用 TEXT（agent 模块 V19/V20 惯例）
-- ===================================================================
CREATE TABLE sw_agent_message (
    id          BIGINT      NOT NULL PRIMARY KEY,
    session_id  BIGINT      NOT NULL,
    role        VARCHAR(20) NOT NULL,
    content     TEXT        NOT NULL,
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

COMMENT ON TABLE sw_agent_message IS 'M07 Agent 会话消息明细（F04 对话交互）';
COMMENT ON COLUMN sw_agent_message.session_id IS '所属会话 id（sw_agent_session）';
COMMENT ON COLUMN sw_agent_message.role IS '消息角色：USER/ASSISTANT/SYSTEM';
COMMENT ON COLUMN sw_agent_message.content IS '消息内容（大文本）';
COMMENT ON COLUMN sw_agent_message.msg_order IS '会话内顺序号（0-based，单调递增）';
