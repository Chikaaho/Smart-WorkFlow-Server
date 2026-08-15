-- Smart-WorkFlow 角色部门关联表（H2/PostgreSQL 双端逐字一致）
-- 数据范围 CUSTOM（自定义部门集合）时，角色可见部门由本表承载；
-- 结构/风格对齐 V1 sys_user_role（同构关联表，含租户列与逻辑删除）。
create table sys_role_dept (
    id              bigint          not null primary key,
    create_time     timestamp       not null default current_timestamp,
    create_by       bigint,
    update_time     timestamp       not null default current_timestamp,
    update_by       bigint,
    deleted         smallint        not null default 0,
    tenant_id       bigint          not null default 0,
    version         bigint          not null default 0,
    role_id         bigint          not null,
    dept_id         bigint          not null
);
create unique index uk_sys_role_dept on sys_role_dept (role_id, dept_id);
