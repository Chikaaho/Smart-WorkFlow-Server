-- ===================================================================
-- Smart-WorkFlow :: Notify 通知表 (H2)
-- ===================================================================
-- M05 Step 1：新建 sw_notify_message 表 + Facade + 数据层。
--
-- 约定：
--   表前缀       = sw_notify_（§3）
--   8 基列在前   = id, create_time, create_by, update_time, update_by,
--                  deleted, tenant_id, version（与 sys_* 对齐）
--   PK           = bigint（ASSIGN_ID 雪花算法）
--   无真实 DB 外键（应用层处理关系）
--   全归 Flyway 管理（非动态宽表）
-- H2 注意：
--   无 COMMENT ON 支持
-- ===================================================================

create table sw_notify_message (
    id                bigint          not null primary key,
    create_time       timestamp       not null default current_timestamp,
    create_by         bigint,
    update_time       timestamp       not null default current_timestamp,
    update_by         bigint,
    deleted           smallint        not null default 0,
    tenant_id         bigint          not null default 0,
    version           bigint          not null default 0,
    recipient_id      bigint          not null,
    title             varchar(200)    not null,
    content           text            not null,
    biz_type          varchar(30)     not null,
    biz_id            varchar(64),
    is_read           boolean         not null default false
);

-- 索引：同租户下按接收人查询
create index idx_sw_notify_msg_recipient on sw_notify_message (tenant_id, recipient_id);
