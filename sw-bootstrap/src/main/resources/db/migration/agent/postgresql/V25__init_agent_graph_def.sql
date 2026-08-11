-- ===================================================================
-- Smart-WorkFlow :: V25: 初始化 Agent 图定义表 (PostgreSQL)
-- M07-F02 Step7：图定义 CRUD + 版本 + 发布骨架（纯存储+管理，无执行语义）
-- 借鉴 sw-bpm V14（sw_bpm_process_def：process_key/name/def_version/status/graph_json），
-- 适配 agent 模块惯例（V19-V24：审计列在后、create_by VARCHAR(64)、大字段 TEXT）
-- 本表走 MyBatis-Plus 常规通道：@TableLogic + 租户拦截器自动处理 deleted/tenant_id
-- ===================================================================
CREATE TABLE sw_agent_graph_def (
    id           BIGINT NOT NULL PRIMARY KEY,
    graph_key    VARCHAR(100) NOT NULL,
    name         VARCHAR(200) NOT NULL,
    def_version  INT NOT NULL DEFAULT 1,
    status       VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    graph_json   TEXT,
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

COMMENT ON TABLE  sw_agent_graph_def IS 'M07 Agent 图定义表（图设计器后端存储）';
COMMENT ON COLUMN sw_agent_graph_def.graph_key IS '图业务 key（服务端生成，发布后冻结）';
COMMENT ON COLUMN sw_agent_graph_def.name IS '图名称';
COMMENT ON COLUMN sw_agent_graph_def.def_version IS '定义版本号（每次发布递增）';
COMMENT ON COLUMN sw_agent_graph_def.status IS '状态：DRAFT（草稿）/ PUBLISHED（已发布）';
COMMENT ON COLUMN sw_agent_graph_def.graph_json IS '图 JSON 文档（ProcessGraph 序列化，config/style 不透明透传）';
