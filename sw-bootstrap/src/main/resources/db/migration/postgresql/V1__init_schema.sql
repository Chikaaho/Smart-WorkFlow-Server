-- ===================================================================
-- Smart-WorkFlow 初始化建表脚本 (PostgreSQL)
-- ===================================================================

-- -------------------- 租户 --------------------
create table sys_tenant (
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
    description     text,
    contact_name    varchar(50),
    contact_phone   varchar(20),
    contact_email   varchar(100),
    expire_time     timestamp,
    domain_name     varchar(100)
);
create unique index uk_sys_tenant_code on sys_tenant (code);

-- -------------------- 部门 --------------------
create table sys_dept (
    id              bigint          not null primary key,
    create_time     timestamp       not null default current_timestamp,
    create_by       bigint,
    update_time     timestamp       not null default current_timestamp,
    update_by       bigint,
    deleted         smallint        not null default 0,
    tenant_id       bigint          not null default 0,
    version         bigint          not null default 0,
    parent_id       bigint          not null default 0,
    name            varchar(100)    not null,
    code            varchar(50),
    sort            integer         not null default 0,
    status          smallint        not null default 0,
    description     text,
    leader_id       bigint
);
create index idx_sys_dept_parent_id on sys_dept (parent_id);

-- -------------------- 岗位 --------------------
create table sys_post (
    id              bigint          not null primary key,
    create_time     timestamp       not null default current_timestamp,
    create_by       bigint,
    update_time     timestamp       not null default current_timestamp,
    update_by       bigint,
    deleted         smallint        not null default 0,
    tenant_id       bigint          not null default 0,
    version         bigint          not null default 0,
    code            varchar(50)     not null,
    name            varchar(100)    not null,
    sort            integer         not null default 0,
    status          smallint        not null default 0,
    description     text
);

-- -------------------- 用户 --------------------
create table sys_user (
    id              bigint          not null primary key,
    create_time     timestamp       not null default current_timestamp,
    create_by       bigint,
    update_time     timestamp       not null default current_timestamp,
    update_by       bigint,
    deleted         smallint        not null default 0,
    tenant_id       bigint          not null default 0,
    version         bigint          not null default 0,
    username        varchar(50)     not null,
    password        varchar(200)    not null,
    real_name       varchar(50),
    email           varchar(100),
    phone           varchar(20),
    avatar          varchar(200),
    sex             smallint        not null default 0,
    status          smallint        not null default 0,
    dept_id         bigint,
    last_login_time timestamp,
    last_login_ip   varchar(50),
    remark          text,
    is_admin        smallint        not null default 0
);
create unique index uk_sys_user_username on sys_user (username);
create index idx_sys_user_dept_id on sys_user (dept_id);

-- -------------------- 角色 --------------------
create table sys_role (
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
    sort            integer         not null default 0,
    status          smallint        not null default 0,
    data_scope      smallint        not null default 0,
    description     text,
    is_builtin      smallint        not null default 0
);
create unique index uk_sys_role_code on sys_role (code);

-- -------------------- 菜单 --------------------
create table sys_menu (
    id              bigint          not null primary key,
    create_time     timestamp       not null default current_timestamp,
    create_by       bigint,
    update_time     timestamp       not null default current_timestamp,
    update_by       bigint,
    deleted         smallint        not null default 0,
    tenant_id       bigint          not null default 0,
    version         bigint          not null default 0,
    parent_id       bigint          not null default 0,
    name            varchar(100)    not null,
    menu_type       smallint        not null default 0,
    path            varchar(200),
    component       varchar(200),
    permission      varchar(100),
    icon            varchar(100),
    sort            integer         not null default 0,
    status          smallint        not null default 0,
    visible         smallint        not null default 1,
    keep_alive      smallint        not null default 0,
    description     text
);
create index idx_sys_menu_parent_id on sys_menu (parent_id);

-- -------------------- 用户角色关联 --------------------
create table sys_user_role (
    id              bigint          not null primary key,
    create_time     timestamp       not null default current_timestamp,
    create_by       bigint,
    update_time     timestamp       not null default current_timestamp,
    update_by       bigint,
    deleted         smallint        not null default 0,
    tenant_id       bigint          not null default 0,
    version         bigint          not null default 0,
    user_id         bigint          not null,
    role_id         bigint          not null
);
create unique index uk_sys_user_role on sys_user_role (user_id, role_id);

-- -------------------- 角色菜单关联 --------------------
create table sys_role_menu (
    id              bigint          not null primary key,
    create_time     timestamp       not null default current_timestamp,
    create_by       bigint,
    update_time     timestamp       not null default current_timestamp,
    update_by       bigint,
    deleted         smallint        not null default 0,
    tenant_id       bigint          not null default 0,
    version         bigint          not null default 0,
    role_id         bigint          not null,
    menu_id         bigint          not null
);
create unique index uk_sys_role_menu on sys_role_menu (role_id, menu_id);

-- -------------------- 字典类型 --------------------
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
    description     text
);
create unique index uk_sys_dict_type_code on sys_dict_type (code);

-- -------------------- 字典数据 --------------------
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
    description     text
);
create index idx_sys_dict_data_dict_code on sys_dict_data (dict_code);
