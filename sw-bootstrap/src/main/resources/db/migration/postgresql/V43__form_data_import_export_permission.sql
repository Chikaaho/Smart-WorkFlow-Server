-- ===================================================================
-- V43: 表单数据导入导出独立权限（P32 / M03-F04-02）— PostgreSQL 方言
--
-- 仅注册按钮权限资源（挂在低代码→表单设计菜单下）；
-- 普通角色授权仍由管理员通过角色菜单管理完成。
-- ===================================================================

INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title,
                      hidden, menu_type, path, component, permission, icon, sort)
SELECT 230, current_timestamp, current_timestamp, 0, 0, 4, 'FormDataTemplate', '下载模板', false, 2,
       '', '', 'form:data:template', '', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 230);

INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title,
                      hidden, menu_type, path, component, permission, icon, sort)
SELECT 231, current_timestamp, current_timestamp, 0, 0, 4, 'FormDataImport', '数据导入', false, 2,
       '', '', 'form:data:import', '', 2
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 231);

INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title,
                      hidden, menu_type, path, component, permission, icon, sort)
SELECT 232, current_timestamp, current_timestamp, 0, 0, 4, 'FormDataExport', '数据导出', false, 2,
       '', '', 'form:data:export', '', 3
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 232);
