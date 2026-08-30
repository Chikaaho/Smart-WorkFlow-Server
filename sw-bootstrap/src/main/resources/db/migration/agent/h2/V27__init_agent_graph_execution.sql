-- ===================================================================
-- Smart-WorkFlow :: V27: 初始化 Agent 图执行记录表 (H2)
-- M07 Step12 图执行历史持久化：执行摘要主表
-- status：'RUNNING'（执行前建行）→ 'SUCCESS' / 'FAILED'（执行后回写终态；
--   成功与失败两类路径均落库，区别于 F04 只写成功分支）
-- input/result_text/error_message：大文本，H2 用 CLOB（agent 模块 V19/V22 惯例）
-- result_text：最终输出列（命名避开 output 保留字——租户拦截器 JSqlParser
--   解析 UPDATE SET 子句时 output 为非法 token，实测踩坑，见执行回执 §6）
-- error_category：错误分类（STEP_LIMIT/LOOP_LIMIT/UNDEFINED_VARIABLE/
--   CONDITION_NO_MATCH/TOPOLOGY_INVALID/MODEL_CALL_FAILED/TOOL_CALL_FAILED/
--   UNKNOWN），由解释器 GraphExecutionException 携带（V27 起 Java 层分类，
--   失败记录完整可查，不靠文本子串匹配）
-- latency_ms：整次执行耗时（毫秒，Service 层 currentTimeMillis 起止差）
-- graph_def_version：执行时图定义版本快照（发布锚点，图可后续再发布）
-- ===================================================================
CREATE TABLE sw_agent_graph_execution (
    id                BIGINT      NOT NULL PRIMARY KEY,
    graph_def_id      BIGINT      NOT NULL,
    graph_def_version INT         NOT NULL,
    status            VARCHAR(20) NOT NULL,
    input             CLOB,
    result_text       CLOB,
    error_category    VARCHAR(50),
    error_message     CLOB,
    latency_ms        BIGINT,
    create_time       TIMESTAMP   NOT NULL,
    create_by         VARCHAR(64),
    update_time       TIMESTAMP,
    update_by         VARCHAR(64),
    deleted           SMALLINT    NOT NULL DEFAULT 0,
    tenant_id         BIGINT      NOT NULL DEFAULT 0,
    version           BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_sw_agent_gexec_graph ON sw_agent_graph_execution (graph_def_id, deleted);
CREATE INDEX idx_sw_agent_gexec_time  ON sw_agent_graph_execution (tenant_id, create_time, deleted);
