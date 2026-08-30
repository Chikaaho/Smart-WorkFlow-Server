-- ===================================================================
-- Smart-WorkFlow :: V23: 初始化 Agent 工具调用日志表 (PostgreSQL)
-- M07 Step4 F04 对话交互：每轮工具调用日志
-- tool_call_args / tool_call_result：JSON 字符串，可能较长，PG 用 TEXT
-- latency_ms：工具执行耗时（毫秒），由 FunctionToolCallback lambda 包装计时
-- ===================================================================
CREATE TABLE sw_agent_tool_call_log (
    id               BIGINT       NOT NULL PRIMARY KEY,
    session_id       BIGINT       NOT NULL,
    tool_name        VARCHAR(100) NOT NULL,
    tool_call_args   TEXT,
    tool_call_result TEXT,
    latency_ms       BIGINT,
    create_time      TIMESTAMP    NOT NULL,
    create_by        VARCHAR(64),
    update_time      TIMESTAMP,
    update_by        VARCHAR(64),
    deleted          SMALLINT     NOT NULL DEFAULT 0,
    tenant_id        BIGINT       NOT NULL DEFAULT 0,
    version          BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_sw_agent_tcl_session ON sw_agent_tool_call_log (session_id, deleted);

COMMENT ON TABLE sw_agent_tool_call_log IS 'M07 Agent 工具调用日志（F04 对话交互）';
COMMENT ON COLUMN sw_agent_tool_call_log.session_id IS '所属会话 id（sw_agent_session）';
COMMENT ON COLUMN sw_agent_tool_call_log.tool_name IS '工具名（白名单表 name）';
COMMENT ON COLUMN sw_agent_tool_call_log.tool_call_args IS '工具入参（JSON 字符串）';
COMMENT ON COLUMN sw_agent_tool_call_log.tool_call_result IS '工具返回（JSON 字符串）';
COMMENT ON COLUMN sw_agent_tool_call_log.latency_ms IS '工具执行耗时（毫秒）';
