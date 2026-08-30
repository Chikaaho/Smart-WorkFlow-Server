-- ===================================================================
-- Smart-WorkFlow :: V36: 初始化 Agent 图调试会话及节点明细表 (PostgreSQL)
-- M07-F02-04 图单步调试闭环：调试会话主表 + 节点轨迹明细
-- sw_agent_graph_debug_session
--   graph_def_id/graph_def_version：调试时图定义快照锚点（同 V27 执行表惯例）
--   graph_json：调试时图 JSON 快照（TEXT，含 elements/edges）
--   status：PAUSED（断点暂停）/ COMPLETED / FAILED / STOPPED（用户停止）/ EXPIRED（TTL 过期）
--   input/breakpoints/state_json/result_text/error_message：大文本，PG 用 TEXT（agent 模块 V19/V22/V27 惯例）
--   breakpoints：断点列表（JSON 数组，元素为 nodeId）
--   state_json：可恢复解释器状态（JSON，含 variables/activePoints/loopCounts/joinCounts/nextNodeIds 等）
--   latency_ms/expires_at/input_tokens/output_tokens：同 V27/V35 执行表语义，调试会话维度
--   expires_at：会话过期时间（TTL，到期后置为 EXPIRED）
-- sw_agent_graph_debug_node
--   debug_session_id：归属调试会话（sw_agent_graph_debug_session）
--   node_seq/branch_id/node_latency_ms/variable_snapshot/input_tokens/output_tokens：同 V28 节点表语义
-- ===================================================================
CREATE TABLE sw_agent_graph_debug_session (
    id                BIGINT      NOT NULL PRIMARY KEY,
    graph_def_id      BIGINT      NOT NULL,
    graph_def_version INT         NOT NULL,
    graph_json        TEXT,
    status            VARCHAR(20) NOT NULL,
    input             TEXT,
    breakpoints       TEXT,
    state_json        TEXT,
    result_text       TEXT,
    error_category    VARCHAR(50),
    error_message     TEXT,
    latency_ms        BIGINT,
    expires_at        TIMESTAMP,
    input_tokens      BIGINT,
    output_tokens     BIGINT,
    create_time       TIMESTAMP   NOT NULL,
    create_by         VARCHAR(64),
    update_time       TIMESTAMP,
    update_by         VARCHAR(64),
    deleted           SMALLINT    NOT NULL DEFAULT 0,
    tenant_id         BIGINT      NOT NULL DEFAULT 0,
    version           BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_gexec_debug_graph   ON sw_agent_graph_debug_session (graph_def_id, deleted);
CREATE INDEX idx_gexec_debug_expires ON sw_agent_graph_debug_session (expires_at);

CREATE TABLE sw_agent_graph_debug_node (
    id                BIGINT       NOT NULL PRIMARY KEY,
    debug_session_id  BIGINT       NOT NULL,
    node_seq          INT          NOT NULL,
    branch_id         VARCHAR(64)  NOT NULL,
    node_id           VARCHAR(100) NOT NULL,
    node_type         VARCHAR(20)  NOT NULL,
    node_latency_ms   BIGINT,
    variable_snapshot TEXT,
    input_tokens      BIGINT,
    output_tokens     BIGINT,
    create_time       TIMESTAMP    NOT NULL,
    create_by         VARCHAR(64),
    update_time       TIMESTAMP,
    update_by         VARCHAR(64),
    deleted           SMALLINT     NOT NULL DEFAULT 0,
    tenant_id         BIGINT       NOT NULL DEFAULT 0,
    version           BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_genode_debug ON sw_agent_graph_debug_node (debug_session_id, node_seq, deleted);

COMMENT ON TABLE sw_agent_graph_debug_session IS 'M07-F02-04 Agent 图调试会话（单步调试闭环）';
COMMENT ON COLUMN sw_agent_graph_debug_session.graph_def_id IS '图定义 id（sw_agent_graph_def）';
COMMENT ON COLUMN sw_agent_graph_debug_session.graph_def_version IS '调试时图定义版本快照';
COMMENT ON COLUMN sw_agent_graph_debug_session.graph_json IS '调试时图 JSON 快照';
COMMENT ON COLUMN sw_agent_graph_debug_session.status IS '调试状态：PAUSED/COMPLETED/FAILED/STOPPED/EXPIRED';
COMMENT ON COLUMN sw_agent_graph_debug_session.input IS '调试入参文本';
COMMENT ON COLUMN sw_agent_graph_debug_session.breakpoints IS '断点列表（JSON 数组，元素为 nodeId）';
COMMENT ON COLUMN sw_agent_graph_debug_session.state_json IS '可恢复解释器状态（JSON，含 variables/activePoints/loopCounts/joinCounts/next 等）';
COMMENT ON COLUMN sw_agent_graph_debug_session.result_text IS '最终输出（成功完成时）';
COMMENT ON COLUMN sw_agent_graph_debug_session.error_category IS '错误分类（解释器携带）';
COMMENT ON COLUMN sw_agent_graph_debug_session.error_message IS '失败原因摘要（不含明文 API Key）';
COMMENT ON COLUMN sw_agent_graph_debug_session.latency_ms IS '整次调试耗时（毫秒）';
COMMENT ON COLUMN sw_agent_graph_debug_session.expires_at IS '会话过期时间（TTL，到期后置为 EXPIRED）';
COMMENT ON COLUMN sw_agent_graph_debug_session.input_tokens IS '本次调试全部 LLM 节点输入 Token 汇总';
COMMENT ON COLUMN sw_agent_graph_debug_session.output_tokens IS '本次调试全部 LLM 节点输出 Token 汇总';

COMMENT ON TABLE sw_agent_graph_debug_node IS 'M07-F02-04 Agent 图调试节点明细（单步调试轨迹）';
COMMENT ON COLUMN sw_agent_graph_debug_node.debug_session_id IS '所属调试会话 id（sw_agent_graph_debug_session）';
COMMENT ON COLUMN sw_agent_graph_debug_node.node_seq IS '本次调试内全局访问步序（1-based）';
COMMENT ON COLUMN sw_agent_graph_debug_node.branch_id IS '并行分支标识（FORK 按出边顺序追加下标）';
COMMENT ON COLUMN sw_agent_graph_debug_node.node_id IS '图节点 id';
COMMENT ON COLUMN sw_agent_graph_debug_node.node_type IS '节点类型（START/LLM/TOOL/CONDITION/LOOP/FORK/JOIN/END）';
COMMENT ON COLUMN sw_agent_graph_debug_node.node_latency_ms IS '节点级耗时（毫秒）';
COMMENT ON COLUMN sw_agent_graph_debug_node.variable_snapshot IS '该节点执行后的变量表快照（JSON）';
COMMENT ON COLUMN sw_agent_graph_debug_node.input_tokens IS '该节点 LLM 输入 Token';
COMMENT ON COLUMN sw_agent_graph_debug_node.output_tokens IS '该节点 LLM 输出 Token';
