-- ===================================================================
-- Smart-WorkFlow :: IoT 设备与控制命令表 (PostgreSQL)
-- ===================================================================
-- 最小设备控制链：设备注册 / 状态 / 控制命令 / 执行结果。
-- 约定与 H2 版本对齐（8 基列 + bigint PK + 无外键）。
-- ===================================================================

create table sw_iot_device (
    id                bigint          not null primary key,
    create_time       timestamp       not null default current_timestamp,
    create_by         bigint,
    update_time       timestamp       not null default current_timestamp,
    update_by         bigint,
    deleted           smallint        not null default 0,
    tenant_id         bigint          not null default 0,
    version           bigint          not null default 0,
    device_key        varchar(64)     not null,
    name              varchar(200)    not null,
    device_type       varchar(50),
    status            varchar(20)     not null default 'OFFLINE',
    last_online_time  timestamp
);

create index idx_sw_iot_device_key on sw_iot_device (tenant_id, device_key);

create table sw_iot_device_command (
    id                bigint          not null primary key,
    create_time       timestamp       not null default current_timestamp,
    create_by         bigint,
    update_time       timestamp       not null default current_timestamp,
    update_by         bigint,
    deleted           smallint        not null default 0,
    tenant_id         bigint          not null default 0,
    version           bigint          not null default 0,
    device_key        varchar(64)     not null,
    command_key       varchar(64)     not null,
    payload           varchar(1000),
    status            varchar(20)     not null default 'PENDING',
    result            varchar(1000),
    approval_biz_id   varchar(64)
);

create index idx_sw_iot_cmd_device on sw_iot_device_command (tenant_id, device_key);
create index idx_sw_iot_cmd_biz on sw_iot_device_command (tenant_id, approval_biz_id);
