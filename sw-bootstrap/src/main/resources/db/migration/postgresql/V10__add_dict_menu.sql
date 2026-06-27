-- ===================================================================
-- V10: 追加"字典管理"二级菜单
--
-- 在"系统管理"(id=1)下新增字典类型列表页菜单行，
-- 指向前端 DictTypeList 组件。
--
-- 约束：
--   · 不 seed sys_role_menu（超管旁路，沿用 V6 决策）
--   · 字典项列表页(DictDataList)是经类型页跳转进入的下钻页，
--     不单独建顶层菜单。
-- ===================================================================

-- 系统管理 → 字典管理
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title, hidden, menu_type, path, component, permission, icon, sort)
VALUES (10, current_timestamp, current_timestamp, 0, 0, 1, 'DictManage', '字典管理', false, 1, 'dict', 'system/views/DictTypeList', 'system:dict:view', 'Collection', 10);
