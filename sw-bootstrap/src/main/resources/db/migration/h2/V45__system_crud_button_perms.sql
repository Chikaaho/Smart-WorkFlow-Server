-- ===================================================================
-- V45: 补齐系统管理 CRUD 按钮权限菜单
--
-- 背景：V2 曾以 status/visible 列风格种子 system:user/role:create|update|delete
-- 按钮菜单（id 100-112），但生产库 sys_menu 为 V15 列风格（title/hidden），
-- 该批行从未落库；后端 UserController/RoleController 的
-- @PreAuthorize("@ss.hasPermi('system:*:create|update|delete')") 因此
-- 对任何非超管用户都无法满足（角色编辑器也无权限树可选）。
--
-- 约束：
--   · 按钮菜单 id 使用 300-305（避开既有 1-232）
--   · 父菜单按 permission 定位（system:user:list / system:role:list），
--     不硬编码父 id，容忍 V2/V15 两代种子的 id 差异
--   · 全部幂等：NOT EXISTS 守卫，可安全重复执行
--   · 同步授予角色 2（管理员/admin），延续 V31「admin 角色拥有全量菜单」语义
-- ===================================================================

-- -------------------- 用户管理按钮 --------------------
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title, hidden, menu_type, path, component, permission, icon, sort)
SELECT 300, current_timestamp, current_timestamp, 0, 0, p.id, 'UserCreate', '用户新增', false, 2, '', '', 'system:user:create', '', 11
FROM sys_menu p
WHERE p.permission = 'system:user:list' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.permission = 'system:user:create' AND m.deleted = 0);

INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title, hidden, menu_type, path, component, permission, icon, sort)
SELECT 301, current_timestamp, current_timestamp, 0, 0, p.id, 'UserUpdate', '用户修改', false, 2, '', '', 'system:user:update', '', 12
FROM sys_menu p
WHERE p.permission = 'system:user:list' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.permission = 'system:user:update' AND m.deleted = 0);

INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title, hidden, menu_type, path, component, permission, icon, sort)
SELECT 302, current_timestamp, current_timestamp, 0, 0, p.id, 'UserDelete', '用户删除', false, 2, '', '', 'system:user:delete', '', 13
FROM sys_menu p
WHERE p.permission = 'system:user:list' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.permission = 'system:user:delete' AND m.deleted = 0);

-- -------------------- 角色管理按钮 --------------------
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title, hidden, menu_type, path, component, permission, icon, sort)
SELECT 303, current_timestamp, current_timestamp, 0, 0, p.id, 'RoleCreate', '角色新增', false, 2, '', '', 'system:role:create', '', 11
FROM sys_menu p
WHERE p.permission = 'system:role:list' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.permission = 'system:role:create' AND m.deleted = 0);

INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title, hidden, menu_type, path, component, permission, icon, sort)
SELECT 304, current_timestamp, current_timestamp, 0, 0, p.id, 'RoleUpdate', '角色修改', false, 2, '', '', 'system:role:update', '', 12
FROM sys_menu p
WHERE p.permission = 'system:role:list' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.permission = 'system:role:update' AND m.deleted = 0);

INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title, hidden, menu_type, path, component, permission, icon, sort)
SELECT 305, current_timestamp, current_timestamp, 0, 0, p.id, 'RoleDelete', '角色删除', false, 2, '', '', 'system:role:delete', '', 13
FROM sys_menu p
WHERE p.permission = 'system:role:list' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.permission = 'system:role:delete' AND m.deleted = 0);

-- -------------------- 授予角色 2（管理员） --------------------
INSERT INTO sys_role_menu (id, create_time, update_time, deleted, version, tenant_id, role_id, menu_id)
SELECT 3000 + m.id, current_timestamp, current_timestamp, 0, 0, 0, 2, m.id
FROM sys_menu m
WHERE m.deleted = 0
  AND m.permission IN ('system:user:create', 'system:user:update', 'system:user:delete',
                       'system:role:create', 'system:role:update', 'system:role:delete')
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 2 AND rm.menu_id = m.id AND rm.deleted = 0)
  AND EXISTS (SELECT 1 FROM sys_role r WHERE r.id = 2 AND r.deleted = 0);
