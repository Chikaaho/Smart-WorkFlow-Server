-- ===================================================================
-- Smart-WorkFlow :: V23: 初始化 Agent 工具调用日志表 (H2)
-- M07 Step4 F04 对话交互：每轮工具调用日志
-- tool_call_args / tool_call_result：JSON 字符串，可能较长，H2 用 CLOB
-- latency_ms：工具执行耗时（毫秒），由 FunctionToolCallback lambda 包装计时
-- ===================================================================
CREATE TABLE sw_agent_tool_call_log (
    id               BIGINT       NOT NULL PRIMARY KEY,
    session_id       BIGINT       NOT NULL,
    tool_name        VARCHAR(100) NOT NULL,
    tool_call_args   CLOB,
    tool_call_result CLOB,
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
