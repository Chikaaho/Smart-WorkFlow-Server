-- ===================================================================
-- Smart-WorkFlow 初始化基础数据 (H2)
-- ===================================================================

-- -------------------- 内置租户 --------------------
insert into sys_tenant (id, create_time, update_time, deleted, tenant_id, version, name, code, status, description, domain_name) values (1, current_timestamp, current_timestamp, 0, 0, 0, '默认租户', 'default', 0, '系统内置默认租户，不可删除', 'localhost');

-- ===================================================================
-- 字典种子数据（租户隔离模型）
-- 字典采用「每租户各自持有一份」的纯隔离模型，不做 tenant_id=0 全局兜底。
-- 以下为 super tenant（tenant_id=0）的字典种子，其内容（dict_code / dict_value / label）
-- 与其它租户的字典一致，仅 tenant_id 不同。
-- 各 INSERT 已显式指定 tenant_id=0，确保隔离边界清晰。
--
-- ⚠️ 已知待办：新增租户时，需在租户创建流程中复制一套基准字典到新租户。
--    本步不实现，仅在此标注。未来在租户创建 BizService 中实现字典模板复制。
-- ===================================================================

-- -------------------- 字典类型（tenant_id=0） --------------------
insert into sys_dict_type (id, create_time, update_time, deleted, tenant_id, version, name, code, status, description) values (1, current_timestamp, current_timestamp, 0, 0, 0, '通用状态', 'sys_common_status', 0, '0=正常 1=停用');
insert into sys_dict_type (id, create_time, update_time, deleted, tenant_id, version, name, code, status, description) values (2, current_timestamp, current_timestamp, 0, 0, 0, '是否', 'sys_yes_no', 0, '0=否 1=是');
insert into sys_dict_type (id, create_time, update_time, deleted, tenant_id, version, name, code, status, description) values (3, current_timestamp, current_timestamp, 0, 0, 0, '性别', 'sys_user_sex', 0, '用户性别');
insert into sys_dict_type (id, create_time, update_time, deleted, tenant_id, version, name, code, status, description) values (4, current_timestamp, current_timestamp, 0, 0, 0, '用户状态', 'sys_user_status', 0, '用户账号状态');
insert into sys_dict_type (id, create_time, update_time, deleted, tenant_id, version, name, code, status, description) values (5, current_timestamp, current_timestamp, 0, 0, 0, '菜单类型', 'sys_menu_type', 0, '菜单节点类型');
insert into sys_dict_type (id, create_time, update_time, deleted, tenant_id, version, name, code, status, description) values (6, current_timestamp, current_timestamp, 0, 0, 0, '数据范围', 'sys_data_scope', 0, '角色数据权限范围');

-- -------------------- 字典数据：通用状态（tenant_id=0） --------------------
insert into sys_dict_data (id, create_time, update_time, deleted, tenant_id, version, dict_code, label, dict_value, sort, status, is_default) values (1, current_timestamp, current_timestamp, 0, 0, 0, 'sys_common_status', '正常', '0', 0, 0, 1);
insert into sys_dict_data (id, create_time, update_time, deleted, tenant_id, version, dict_code, label, dict_value, sort, status, is_default) values (2, current_timestamp, current_timestamp, 0, 0, 0, 'sys_common_status', '停用', '1', 1, 0, 0);

-- -------------------- 字典数据：是否（tenant_id=0） --------------------
insert into sys_dict_data (id, create_time, update_time, deleted, tenant_id, version, dict_code, label, dict_value, sort, status, is_default) values (3, current_timestamp, current_timestamp, 0, 0, 0, 'sys_yes_no', '否', '0', 0, 0, 1);
insert into sys_dict_data (id, create_time, update_time, deleted, tenant_id, version, dict_code, label, dict_value, sort, status, is_default) values (4, current_timestamp, current_timestamp, 0, 0, 0, 'sys_yes_no', '是', '1', 1, 0, 0);

-- -------------------- 字典数据：性别（tenant_id=0） --------------------
insert into sys_dict_data (id, create_time, update_time, deleted, tenant_id, version, dict_code, label, dict_value, sort, status, is_default) values (5, current_timestamp, current_timestamp, 0, 0, 0, 'sys_user_sex', '未知', '0', 0, 0, 1);
insert into sys_dict_data (id, create_time, update_time, deleted, tenant_id, version, dict_code, label, dict_value, sort, status, is_default) values (6, current_timestamp, current_timestamp, 0, 0, 0, 'sys_user_sex', '男', '1', 1, 0, 0);
insert into sys_dict_data (id, create_time, update_time, deleted, tenant_id, version, dict_code, label, dict_value, sort, status, is_default) values (7, current_timestamp, current_timestamp, 0, 0, 0, 'sys_user_sex', '女', '2', 2, 0, 0);

-- -------------------- 字典数据：用户状态（tenant_id=0） --------------------
insert into sys_dict_data (id, create_time, update_time, deleted, tenant_id, version, dict_code, label, dict_value, sort, status, is_default) values (8, current_timestamp, current_timestamp, 0, 0, 0, 'sys_user_status', '正常', '0', 0, 0, 1);
insert into sys_dict_data (id, create_time, update_time, deleted, tenant_id, version, dict_code, label, dict_value, sort, status, is_default) values (9, current_timestamp, current_timestamp, 0, 0, 0, 'sys_user_status', '停用', '1', 1, 0, 0);
insert into sys_dict_data (id, create_time, update_time, deleted, tenant_id, version, dict_code, label, dict_value, sort, status, is_default) values (10, current_timestamp, current_timestamp, 0, 0, 0, 'sys_user_status', '锁定', '2', 2, 0, 0);

-- -------------------- 字典数据：菜单类型（tenant_id=0） --------------------
insert into sys_dict_data (id, create_time, update_time, deleted, tenant_id, version, dict_code, label, dict_value, sort, status, is_default) values (11, current_timestamp, current_timestamp, 0, 0, 0, 'sys_menu_type', '目录', '0', 0, 0, 1);
insert into sys_dict_data (id, create_time, update_time, deleted, tenant_id, version, dict_code, label, dict_value, sort, status, is_default) values (12, current_timestamp, current_timestamp, 0, 0, 0, 'sys_menu_type', '菜单', '1', 1, 0, 0);
insert into sys_dict_data (id, create_time, update_time, deleted, tenant_id, version, dict_code, label, dict_value, sort, status, is_default) values (13, current_timestamp, current_timestamp, 0, 0, 0, 'sys_menu_type', '按钮', '2', 2, 0, 0);

-- -------------------- 字典数据：数据范围（tenant_id=0） --------------------
insert into sys_dict_data (id, create_time, update_time, deleted, tenant_id, version, dict_code, label, dict_value, sort, status, is_default) values (14, current_timestamp, current_timestamp, 0, 0, 0, 'sys_data_scope', '全部数据', '0', 0, 0, 1);
insert into sys_dict_data (id, create_time, update_time, deleted, tenant_id, version, dict_code, label, dict_value, sort, status, is_default) values (15, current_timestamp, current_timestamp, 0, 0, 0, 'sys_data_scope', '本部门及以下', '1', 1, 0, 0);
insert into sys_dict_data (id, create_time, update_time, deleted, tenant_id, version, dict_code, label, dict_value, sort, status, is_default) values (16, current_timestamp, current_timestamp, 0, 0, 0, 'sys_data_scope', '本部门', '2', 2, 0, 0);
insert into sys_dict_data (id, create_time, update_time, deleted, tenant_id, version, dict_code, label, dict_value, sort, status, is_default) values (17, current_timestamp, current_timestamp, 0, 0, 0, 'sys_data_scope', '仅本人', '3', 3, 0, 0);

-- -------------------- 内置角色：超级管理员 --------------------
insert into sys_role (id, create_time, update_time, deleted, tenant_id, version, name, code, sort, status, data_scope, description, is_builtin) values (1, current_timestamp, current_timestamp, 0, 0, 0, '超级管理员', 'admin', 0, 0, 0, '系统内置超级管理员角色，拥有所有权限', 1);

-- -------------------- 内置菜单：系统管理 --------------------
-- 一级目录
insert into sys_menu (id, create_time, update_time, deleted, tenant_id, version, parent_id, name, menu_type, path, component, permission, icon, sort, status, visible, keep_alive, description) values (1, current_timestamp, current_timestamp, 0, 0, 0, 0, '系统管理', 0, '/system', '', '', 'system', 100, 0, 1, 1, '系统管理目录');
-- 二级菜单
insert into sys_menu (id, create_time, update_time, deleted, tenant_id, version, parent_id, name, menu_type, path, component, permission, icon, sort, status, visible, keep_alive, description) values (10, current_timestamp, current_timestamp, 0, 0, 0, 1, '用户管理', 1, 'user', 'system/user/index', 'system:user:list', 'user', 1, 0, 1, 1, '系统用户管理');
insert into sys_menu (id, create_time, update_time, deleted, tenant_id, version, parent_id, name, menu_type, path, component, permission, icon, sort, status, visible, keep_alive, description) values (11, current_timestamp, current_timestamp, 0, 0, 0, 1, '角色管理', 1, 'role', 'system/role/index', 'system:role:list', 'role', 2, 0, 1, 1, '系统角色管理');
insert into sys_menu (id, create_time, update_time, deleted, tenant_id, version, parent_id, name, menu_type, path, component, permission, icon, sort, status, visible, keep_alive, description) values (12, current_timestamp, current_timestamp, 0, 0, 0, 1, '菜单管理', 1, 'menu', 'system/menu/index', 'system:menu:list', 'menu', 3, 0, 1, 1, '系统菜单管理');
insert into sys_menu (id, create_time, update_time, deleted, tenant_id, version, parent_id, name, menu_type, path, component, permission, icon, sort, status, visible, keep_alive, description) values (13, current_timestamp, current_timestamp, 0, 0, 0, 1, '部门管理', 1, 'dept', 'system/dept/index', 'system:dept:list', 'dept', 4, 0, 1, 1, '机构部门管理');
insert into sys_menu (id, create_time, update_time, deleted, tenant_id, version, parent_id, name, menu_type, path, component, permission, icon, sort, status, visible, keep_alive, description) values (14, current_timestamp, current_timestamp, 0, 0, 0, 1, '岗位管理', 1, 'post', 'system/post/index', 'system:post:list', 'post', 5, 0, 1, 1, '岗位管理');
insert into sys_menu (id, create_time, update_time, deleted, tenant_id, version, parent_id, name, menu_type, path, component, permission, icon, sort, status, visible, keep_alive, description) values (15, current_timestamp, current_timestamp, 0, 0, 0, 1, '字典管理', 1, 'dict', 'system/dict/index', 'system:dict:list', 'dict', 6, 0, 1, 1, '数据字典管理');
insert into sys_menu (id, create_time, update_time, deleted, tenant_id, version, parent_id, name, menu_type, path, component, permission, icon, sort, status, visible, keep_alive, description) values (16, current_timestamp, current_timestamp, 0, 0, 0, 1, '租户管理', 1, 'tenant', 'system/tenant/index', 'system:tenant:list', 'tenant', 7, 0, 1, 1, '多租户管理');
-- 按钮权限：用户管理
insert into sys_menu (id, create_time, update_time, deleted, tenant_id, version, parent_id, name, menu_type, path, component, permission, icon, sort, status, visible, keep_alive, description) values (100, current_timestamp, current_timestamp, 0, 0, 0, 10, '用户新增', 2, '', '', 'system:user:create', '', 1, 0, 1, 0, '');
insert into sys_menu (id, create_time, update_time, deleted, tenant_id, version, parent_id, name, menu_type, path, component, permission, icon, sort, status, visible, keep_alive, description) values (101, current_timestamp, current_timestamp, 0, 0, 0, 10, '用户修改', 2, '', '', 'system:user:update', '', 2, 0, 1, 0, '');
insert into sys_menu (id, create_time, update_time, deleted, tenant_id, version, parent_id, name, menu_type, path, component, permission, icon, sort, status, visible, keep_alive, description) values (102, current_timestamp, current_timestamp, 0, 0, 0, 10, '用户删除', 2, '', '', 'system:user:delete', '', 3, 0, 1, 0, '');
-- 按钮权限：角色管理
insert into sys_menu (id, create_time, update_time, deleted, tenant_id, version, parent_id, name, menu_type, path, component, permission, icon, sort, status, visible, keep_alive, description) values (110, current_timestamp, current_timestamp, 0, 0, 0, 11, '角色新增', 2, '', '', 'system:role:create', '', 1, 0, 1, 0, '');
insert into sys_menu (id, create_time, update_time, deleted, tenant_id, version, parent_id, name, menu_type, path, component, permission, icon, sort, status, visible, keep_alive, description) values (111, current_timestamp, current_timestamp, 0, 0, 0, 11, '角色修改', 2, '', '', 'system:role:update', '', 2, 0, 1, 0, '');
insert into sys_menu (id, create_time, update_time, deleted, tenant_id, version, parent_id, name, menu_type, path, component, permission, icon, sort, status, visible, keep_alive, description) values (112, current_timestamp, current_timestamp, 0, 0, 0, 11, '角色删除', 2, '', '', 'system:role:delete', '', 3, 0, 1, 0, '');

-- -------------------- 超管角色绑定所有菜单 --------------------
insert into sys_role_menu (id, create_time, update_time, deleted, tenant_id, version, role_id, menu_id) values (1, current_timestamp, current_timestamp, 0, 0, 0, 1, 1);
insert into sys_role_menu (id, create_time, update_time, deleted, tenant_id, version, role_id, menu_id) values (2, current_timestamp, current_timestamp, 0, 0, 0, 1, 10);
insert into sys_role_menu (id, create_time, update_time, deleted, tenant_id, version, role_id, menu_id) values (3, current_timestamp, current_timestamp, 0, 0, 0, 1, 11);
insert into sys_role_menu (id, create_time, update_time, deleted, tenant_id, version, role_id, menu_id) values (4, current_timestamp, current_timestamp, 0, 0, 0, 1, 12);
insert into sys_role_menu (id, create_time, update_time, deleted, tenant_id, version, role_id, menu_id) values (5, current_timestamp, current_timestamp, 0, 0, 0, 1, 13);
insert into sys_role_menu (id, create_time, update_time, deleted, tenant_id, version, role_id, menu_id) values (6, current_timestamp, current_timestamp, 0, 0, 0, 1, 14);
insert into sys_role_menu (id, create_time, update_time, deleted, tenant_id, version, role_id, menu_id) values (7, current_timestamp, current_timestamp, 0, 0, 0, 1, 15);
insert into sys_role_menu (id, create_time, update_time, deleted, tenant_id, version, role_id, menu_id) values (8, current_timestamp, current_timestamp, 0, 0, 0, 1, 16);
insert into sys_role_menu (id, create_time, update_time, deleted, tenant_id, version, role_id, menu_id) values (9, current_timestamp, current_timestamp, 0, 0, 0, 1, 100);
insert into sys_role_menu (id, create_time, update_time, deleted, tenant_id, version, role_id, menu_id) values (10, current_timestamp, current_timestamp, 0, 0, 0, 1, 101);
insert into sys_role_menu (id, create_time, update_time, deleted, tenant_id, version, role_id, menu_id) values (11, current_timestamp, current_timestamp, 0, 0, 0, 1, 102);
insert into sys_role_menu (id, create_time, update_time, deleted, tenant_id, version, role_id, menu_id) values (12, current_timestamp, current_timestamp, 0, 0, 0, 1, 110);
insert into sys_role_menu (id, create_time, update_time, deleted, tenant_id, version, role_id, menu_id) values (13, current_timestamp, current_timestamp, 0, 0, 0, 1, 111);
insert into sys_role_menu (id, create_time, update_time, deleted, tenant_id, version, role_id, menu_id) values (14, current_timestamp, current_timestamp, 0, 0, 0, 1, 112);
