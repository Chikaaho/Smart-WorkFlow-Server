-- ===================================================================
-- V3: 外部数据源连接 + SQL 执行审计 (H2)
-- ===================================================================

-- -------------------- 外部数据源连接信息 --------------------
create table sw_bpm_ext_datasource (
    id              bigint          not null primary key,
    create_time     timestamp       not null default current_timestamp,
    create_by       bigint,
    update_time     timestamp       not null default current_timestamp,
    update_by       bigint,
    deleted         smallint         not null default 0,
    tenant_id       bigint          not null default 0,
    version         bigint          not null default 0,
    name            varchar(100)    not null,
    type            varchar(50)     not null,
    jdbc_url        varchar(500)    not null,
    driver_class    varchar(200)    not null,
    username        varchar(100)    not null,
    password_cipher clob            not null,
    read_only       smallint         not null default 1,
    enabled         smallint         not null default 1
);
create unique index uk_sw_bpm_ext_ds_name on sw_bpm_ext_datasource (name);

-- -------------------- SQL 执行审计日志 --------------------
create table sw_bpm_ext_sql_execution_audit (
    id                  bigint          not null primary key,
    create_time         timestamp       not null default current_timestamp,
    create_by           bigint,
    update_time         timestamp       not null default current_timestamp,
    update_by           bigint,
    deleted             smallint         not null default 0,
    tenant_id           bigint          not null default 0,
    version             bigint          not null default 0,
    datasource_id       bigint          not null,
    datasource_name     varchar(100)    not null,
    sql_text            clob            not null,
    row_count           integer,
    execution_time_ms   bigint,
    success             smallint         not null default 0,
    error_message       clob,
    operator_id         bigint          not null,
    operator_name       varchar(50)     not null
);
create index idx_sw_bpm_ext_audit_ds_id on sw_bpm_ext_sql_execution_audit (datasource_id);
create index idx_sw_bpm_ext_audit_operator_id on sw_bpm_ext_sql_execution_audit (operator_id);
create index idx_sw_bpm_ext_audit_create_time on sw_bpm_ext_sql_execution_audit (create_time);
