-- ===================================================================
-- Smart-WorkFlow :: DictFacade 验证测试用 Schema（H2）
-- 仅包含字典模块所需的表，与生产 V1__init_schema.sql 的定义一致。
-- ===================================================================

create table sys_dict_type (
    id              bigint          not null primary key,
    create_time     timestamp       not null default current_timestamp,
    create_by       bigint,
    update_time     timestamp       not null default current_timestamp,
    update_by       bigint,
    deleted         smallint        not null default 0,
    tenant_id       bigint          not null default 0,
    version         bigint          not null default 0,
    name            varchar(100)    not null,
    code            varchar(50)     not null,
    status          smallint        not null default 0,
    description     clob
);
create unique index uk_sys_dict_type_code on sys_dict_type (code);

create table sys_dict_data (
    id              bigint          not null primary key,
    create_time     timestamp       not null default current_timestamp,
    create_by       bigint,
    update_time     timestamp       not null default current_timestamp,
    update_by       bigint,
    deleted         smallint        not null default 0,
    tenant_id       bigint          not null default 0,
    version         bigint          not null default 0,
    dict_code       varchar(50)     not null,
    label           varchar(100)    not null,
    dict_value      varchar(100)    not null,
    sort            integer         not null default 0,
    status          smallint        not null default 0,
    is_default      smallint        not null default 0,
    css_class       varchar(50),
    list_class      varchar(50),
    description     clob
);
create index idx_sys_dict_data_dict_code on sys_dict_data (dict_code);
