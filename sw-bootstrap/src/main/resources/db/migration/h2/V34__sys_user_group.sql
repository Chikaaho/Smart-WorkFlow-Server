-- P28/I36（D112）：用户组维护与成员绑定基础闭环 —— 主表 + 成员关系表。
-- 语义：租户内扁平虚拟用户组（无层级/无负责人），业务标识租户内稳定唯一；
--       用户-组多对多成员关系；全部逻辑删除；双方言（H2/PostgreSQL）逐字一致。
-- 唯一性采用 (tenant_id, deleted) 复合模式（对齐 V13/V32 逻辑删除唯一语义）：
--   同一租户内同一业务标识最多一条 deleted=0 生效行；deleted=1 历史行允许同 key 共存。

CREATE TABLE sys_user_group (
    id BIGINT NOT NULL,
    create_time TIMESTAMP,
    create_by BIGINT,
    update_time TIMESTAMP,
    update_by BIGINT,
    deleted SMALLINT NOT NULL DEFAULT 0,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    group_code VARCHAR(64) NOT NULL,
    group_name VARCHAR(64) NOT NULL,
    status SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(255)
);
CREATE UNIQUE INDEX uk_sys_user_group_code ON sys_user_group (tenant_id, group_code, deleted);
CREATE INDEX idx_sys_user_group_name ON sys_user_group (tenant_id, group_name, deleted);
CREATE INDEX idx_sys_user_group_status ON sys_user_group (tenant_id, status, deleted);

CREATE TABLE sys_user_group_member (
    id BIGINT NOT NULL,
    create_time TIMESTAMP,
    create_by BIGINT,
    update_time TIMESTAMP,
    update_by BIGINT,
    deleted SMALLINT NOT NULL DEFAULT 0,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    group_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL
);
CREATE UNIQUE INDEX uk_sys_user_group_member ON sys_user_group_member (tenant_id, group_id, user_id, deleted);
CREATE INDEX idx_sys_user_group_member_group ON sys_user_group_member (tenant_id, group_id, deleted);
CREATE INDEX idx_sys_user_group_member_user ON sys_user_group_member (tenant_id, user_id, deleted);
