-- ===================================================================
-- V4: 系统管理种子数据（根部门 + 管理员用户）
-- 依赖 V1__init_schema.sql 中已建立的 sys_dept / sys_user / sys_user_role 表
-- ===================================================================

-- -------------------- 根部门 --------------------
insert into sys_dept (id, create_time, update_time, deleted, tenant_id, version,
                      parent_id, name, code, sort, status, description)
values (1, current_timestamp, current_timestamp, 0, 0, 0,
        0, '根部门', 'root', 0, 0, '系统根部门');

-- -------------------- 管理员用户 --------------------
-- 明文密码: admin123（仅 dev 使用）
-- BCrypt 散列: strength=10, $2a$10$GQx6ILw5jsPhqHxJ6/AcmOzSM8xRVRwqChiH/B9ylh0srY0/NqXiK
insert into sys_user (id, create_time, update_time, deleted, tenant_id, version,
                      username, password, real_name, dept_id, status, is_admin)
values (1, current_timestamp, current_timestamp, 0, 0, 0,
        'admin', '$2a$10$GQx6ILw5jsPhqHxJ6/AcmOzSM8xRVRwqChiH/B9ylh0srY0/NqXiK',
        '系统管理员', 1, 0, 1);

-- -------------------- 关联管理员用户到超管角色 --------------------
insert into sys_user_role (id, create_time, update_time, deleted, tenant_id, version,
                           user_id, role_id)
values (1, current_timestamp, current_timestamp, 0, 0, 0, 1, 1);
