-- ===================================================================
-- Smart-WorkFlow :: V25: 初始化 Agent 图定义表 (H2)
-- M07-F02 Step7：图定义 CRUD + 版本 + 发布骨架（纯存储+管理，无执行语义）
-- 借鉴 sw-bpm V14（sw_bpm_process_def：process_key/name/def_version/status/graph_json），
-- 适配 agent 模块惯例（V19-V24：审计列在后、create_by VARCHAR(64)、大字段 CLOB）
-- 本表走 MyBatis-Plus 常规通道：@TableLogic + 租户拦截器自动处理 deleted/tenant_id
-- ===================================================================
CREATE TABLE sw_agent_graph_def (
    id           BIGINT NOT NULL PRIMARY KEY,
    graph_key    VARCHAR(100) NOT NULL,
    name         VARCHAR(200) NOT NULL,
    def_version  INT NOT NULL DEFAULT 1,
    status       VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    graph_json   CLOB,
    create_time  TIMESTAMP,
    create_by    VARCHAR(64),
    update_time  TIMESTAMP,
    update_by    VARCHAR(64),
    deleted      SMALLINT NOT NULL DEFAULT 0,
    tenant_id    BIGINT NOT NULL DEFAULT 0,
    version      BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_sw_agent_graph_key ON sw_agent_graph_def (tenant_id, graph_key);
CREATE INDEX idx_sw_agent_graph_tenant_deleted ON sw_agent_graph_def (tenant_id, deleted);
