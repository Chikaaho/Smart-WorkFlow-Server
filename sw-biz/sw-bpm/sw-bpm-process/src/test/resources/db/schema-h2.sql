-- ===================================================================
-- Smart-WorkFlow :: BpmInstanceService 验证测试用 Schema（H2）
-- 仅包含 BPM 实例记录表，与 Flyway V8 的定义一致。
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
