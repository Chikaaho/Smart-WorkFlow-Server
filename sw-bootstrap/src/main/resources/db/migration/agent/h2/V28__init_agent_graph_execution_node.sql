-- ===================================================================
-- Smart-WorkFlow :: V28: 初始化 Agent 图执行节点明细表 (H2)
-- M07 Step12 图执行历史持久化：节点级执行轨迹
-- node_seq：本次执行内全局访问步序（1-based，节点出队即分配；END 也占一条）
-- branch_id：并行分支标识（FORK 扇出按出边在 elements 中的出现顺序追加下标，
--   如 "0" / "0-1" / "0-2"；非 FORK 路径恒为 "0"；JOIN 汇合后沿用最后到达
--   分支的 branch_id；同一分支内 LOOP 迭代 = 多条 node_seq 递增的记录）
-- node_latency_ms：节点级耗时（出队到本步路由完成，毫秒）
-- variable_snapshot：该节点执行后的变量表快照（JSON，Map<String,String>）
-- ===================================================================
CREATE TABLE sw_agent_graph_execution_node (
    id                BIGINT       NOT NULL PRIMARY KEY,
    execution_id      BIGINT       NOT NULL,
    node_seq          INT          NOT NULL,
    branch_id         VARCHAR(64)  NOT NULL,
    node_id           VARCHAR(100) NOT NULL,
    node_type         VARCHAR(20)  NOT NULL,
    node_latency_ms   BIGINT,
    variable_snapshot CLOB,
    create_time       TIMESTAMP    NOT NULL,
    create_by         VARCHAR(64),
    update_time       TIMESTAMP,
    update_by         VARCHAR(64),
    deleted           SMALLINT     NOT NULL DEFAULT 0,
    tenant_id         BIGINT       NOT NULL DEFAULT 0,
    version           BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_sw_agent_genode_exec ON sw_agent_graph_execution_node (execution_id, node_seq, deleted);
