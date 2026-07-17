-- ===================================================================
-- V15: 系统管理子菜单 —— 用户管理 / 角色管理 / 部门管理 / 岗位管理
--
-- 约束：
--   · id 使用 11-14（避开 1-9, 15+ 以免冲突）
--   · parent_id = 1（System 菜单，详见 V6）
--   · component 路径使用 vue 组件路径格式（无后缀无前导斜杠）
--   · 不 seed sys_role_menu（超管旁路）
-- ===================================================================

INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title, hidden, menu_type, path, component, permission, icon, sort)
VALUES (11, current_timestamp, current_timestamp, 0, 0, 1, 'User', '用户管理', false, 1, 'user', 'system/views/UserList', 'system:user:list', 'User', 10);

INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title, hidden, menu_type, path, component, permission, icon, sort)
VALUES (12, current_timestamp, current_timestamp, 0, 0, 1, 'Role', '角色管理', false, 1, 'role', 'system/views/RoleList', 'system:role:list', 'Avatar', 20);

INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title, hidden, menu_type, path, component, permission, icon, sort)
VALUES (13, current_timestamp, current_timestamp, 0, 0, 1, 'Dept', '部门管理', false, 1, 'dept', 'system/views/DeptList', 'system:dept:list', 'Collection', 30);

INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title, hidden, menu_type, path, component, permission, icon, sort)
VALUES (14, current_timestamp, current_timestamp, 0, 0, 1, 'Post', '岗位管理', false, 1, 'post', 'system/views/PostList', 'system:post:list', 'Tickets', 40);
