-- ===================================================================
-- Smart-WorkFlow :: BPM 流程定义表 (PostgreSQL)
-- ===================================================================
-- cut A：流程定义图模型存储。
-- 本表走 MyBatis-Plus 常规通道：@TableLogic + 租户拦截器自动处理
-- deleted/tenant_id，不写裸 SQL。
--
-- 约定：
--   表前缀       = sw_bpm_（§3）
--   8 基列在前   = id, create_time, create_by, update_time, update_by,
--                  deleted, tenant_id, version（与现有 sw_bpm_ 表对齐）
--   PK           = bigint（ASSIGN_ID 雪花算法）
--   graph_json   = clob（H2）/ text（PG），存储 ProcessGraph JSON
--   status       = DRAFT | PUBLISHED；本刀恒 DRAFT
-- ===================================================================

create table sw_bpm_process_def (
    id                   bigint          not null primary key,
    create_time          timestamp       not null default current_timestamp,
    create_by            bigint,
    update_time          timestamp       not null default current_timestamp,
    update_by            bigint,
    deleted              smallint        not null default 0,
    tenant_id            bigint          not null default 0,
    version              bigint          not null default 0,
    process_key          varchar(200)    not null,
    name                 varchar(200)    not null,
    form_key             varchar(200)    not null,
    def_version          int             not null default 1,
    status               varchar(20)     not null default 'DRAFT',
    deployment_id        varchar(64),
    process_definition_id varchar(64),
    graph_json           text
);

comment on table  sw_bpm_process_def                 is '流程定义表（图模型存储）';
comment on column sw_bpm_process_def.process_key      is '流程业务 key（发布后冻结）';
comment on column sw_bpm_process_def.name             is '流程名称';
comment on column sw_bpm_process_def.form_key         is '绑定表单 formKey';
comment on column sw_bpm_process_def.def_version      is '定义版本号（默认 1）';
comment on column sw_bpm_process_def.status           is '状态：DRAFT（草稿）/ PUBLISHED（已发布）';
comment on column sw_bpm_process_def.deployment_id    is 'Flowable 部署 ID（cut B 回填）';
comment on column sw_bpm_process_def.process_definition_id is 'Flowable 流程定义 ID（cut B 回填）';
comment on column sw_bpm_process_def.graph_json       is '图 JSON 文档（ProcessGraph 序列化）';

create index idx_sw_bpm_proc_def_key on sw_bpm_process_def (process_key);
create index idx_sw_bpm_proc_def_form  on sw_bpm_process_def (form_key);
