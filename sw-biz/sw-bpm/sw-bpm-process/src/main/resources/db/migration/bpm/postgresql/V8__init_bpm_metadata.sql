-- ===================================================================
-- Smart-WorkFlow :: BPM 模块元数据表 (PostgreSQL)
-- ===================================================================
-- M04 第三环第 2 步：表单↔流程绑定 + 流程实例记录。
-- 仅建表与数据访问层，不写发起逻辑、不写 listener、不写 Controller。
--
-- 约定：
--   表前缀       = sw_bpm_（§3）
--   8 基列在前   = id, create_time, create_by, update_time, update_by,
--                  deleted, tenant_id, version（与 sys_* 对齐）
--   PK           = bigint（ASSIGN_ID 雪花算法）
--   无真实 DB 外键（应用层处理关系）
--   全归 Flyway 管理（非动态宽表）
-- ===================================================================

-- ==================== 1. 表单↔流程绑定表 ====================
-- 将表单（by form_key）绑定到一条 BPMN 流程定义（by process_def_key）。
-- 一个表单只能有一条启用绑定（active=true），停用后可用新绑定替换。
create table sw_bpm_form_binding (
    id                bigint          not null primary key,
    create_time       timestamp       not null default current_timestamp,
    create_by         bigint,
    update_time       timestamp       not null default current_timestamp,
    update_by         bigint,
    deleted           smallint        not null default 0,
    tenant_id         bigint          not null default 0,
    version           bigint          not null default 0,
    form_key          varchar(200)    not null,
    process_def_key   varchar(200)    not null,
    active            boolean         not null default true
);

comment on table  sw_bpm_form_binding                is '表单↔流程绑定表';
comment on column sw_bpm_form_binding.form_key        is '表单业务标识（对应 FormSubmittedEvent.formKey）';
comment on column sw_bpm_form_binding.process_def_key is 'BPMN 流程定义 key（Flowable 部署用）';
comment on column sw_bpm_form_binding.active          is '是否启用：true=启用（唯一，同 form_key+tenant 仅一条）, false=停用';

-- 唯一索引：同租户下同表单只能有一条启用绑定
create unique index uk_sw_bpm_binding_active on sw_bpm_form_binding (tenant_id, form_key) where active = true;

-- ==================== 2. 流程实例记录表 ====================
-- 记录我方发起的每个 Flowable 流程实例，供"我发起的"/监控查询。
-- 与 Flowable ACT_HI_PROCINST 保持 process_instance_id 映射。
create table sw_bpm_instance (
    id                   bigint          not null primary key,
    create_time          timestamp       not null default current_timestamp,
    create_by            bigint,
    update_time          timestamp       not null default current_timestamp,
    update_by            bigint,
    deleted              smallint        not null default 0,
    tenant_id            bigint          not null default 0,
    version              bigint          not null default 0,
    process_instance_id  varchar(64)     not null,
    process_def_key      varchar(200)    not null,
    business_key         varchar(36)     not null,
    form_key             varchar(200)    not null,
    initiator_id         bigint          not null,
    status               varchar(20)     not null default 'RUNNING'
);

comment on table  sw_bpm_instance                       is '流程实例记录表';
comment on column sw_bpm_instance.process_instance_id    is 'Flowable 流程实例 ID（对应 ACT_HI_PROCINST.ID_）';
comment on column sw_bpm_instance.process_def_key        is 'BPMN 流程定义 key';
comment on column sw_bpm_instance.business_key           is '业务键（= 表单动态宽表 recordId，VARCHAR 36）';
comment on column sw_bpm_instance.form_key               is '表单业务标识';
comment on column sw_bpm_instance.initiator_id           is '发起人（指向 sys_user.id）';
comment on column sw_bpm_instance.status                 is '实例状态：RUNNING(运行中) / APPROVED(已通过) / REJECTED(已驳回)';

create index idx_sw_bpm_inst_process_inst on sw_bpm_instance (process_instance_id);
create index idx_sw_bpm_inst_business_key on sw_bpm_instance (business_key);
create index idx_sw_bpm_inst_tenant_status on sw_bpm_instance (tenant_id, status);
