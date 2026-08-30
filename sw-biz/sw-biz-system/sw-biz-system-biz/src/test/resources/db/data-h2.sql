-- ===================================================================
-- Smart-WorkFlow :: DictFacade 验证测试用种子数据（H2）
-- 与生产 V2__init_data.sql 的字典部分一致，仅含 super tenant（tenant_id=0）的字典数据。
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
