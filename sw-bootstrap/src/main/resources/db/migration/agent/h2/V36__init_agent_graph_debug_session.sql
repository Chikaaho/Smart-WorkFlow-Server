-- ===================================================================
-- Smart-WorkFlow :: V36: 初始化 Agent 图调试会话及节点明细表 (H2)
-- M07-F02-04 图单步调试闭环：调试会话主表 + 节点轨迹明细
-- sw_agent_graph_debug_session
--   graph_def_id/graph_def_version：调试时图定义快照锚点（同 V27 执行表惯例）
--   graph_json：调试时图 JSON 快照（CLOB，含 elements/edges）
--   status：PAUSED（断点暂停）/ COMPLETED / FAILED / STOPPED（用户停止）/ EXPIRED（TTL 过期）
--   input/breakpoints/state_json/result_text/error_message：大文本，H2 用 CLOB（agent 模块 V19/V22/V27 惯例）
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
    graph_json        CLOB,
    status            VARCHAR(20) NOT NULL,
    input             CLOB,
    breakpoints       CLOB,
    state_json        CLOB,
    result_text       CLOB,
    error_category    VARCHAR(50),
    error_message     CLOB,
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
    variable_snapshot CLOB,
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
