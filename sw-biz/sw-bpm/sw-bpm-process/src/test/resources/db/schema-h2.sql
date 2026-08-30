-- ===================================================================
-- Smart-WorkFlow :: BPM 验证测试用 Schema（H2）
-- 包含 BPM 实例记录表与表单↔流程绑定表，均与 Flyway V8 的定义一致。
-- 绑定表含生成列 active_key + 唯一索引，等价实现「仅 active=true 唯一」。
-- ===================================================================

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

create index idx_sw_bpm_inst_process_inst on sw_bpm_instance (process_instance_id);
create index idx_sw_bpm_inst_business_key on sw_bpm_instance (business_key);
create index idx_sw_bpm_inst_tenant_status on sw_bpm_instance (tenant_id, status);

-- ==================== 2. 表单↔流程绑定表 ====================
-- 与 Flyway V8（H2 方言）逐字一致：active=true 时 active_key 非空且唯一，
-- active=false 时 active_key 为 NULL，多条非 active 历史可共存。
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
    active            boolean         not null default true,
    active_key        varchar(265)    generated always as (case when active then (cast(tenant_id as varchar(64)) || ':' || form_key) end)
);

create unique index uk_sw_bpm_binding_active on sw_bpm_form_binding (active_key);
