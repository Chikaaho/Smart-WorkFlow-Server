-- ===================================================================
-- V39: 通知批量发送页面与独立权限（M05-F01-01）— PostgreSQL 方言
--
-- 仅注册生产菜单/按钮资源；普通角色授权仍由管理员通过角色菜单管理完成。
-- ===================================================================

INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title,
                      hidden, menu_type, path, component, permission, icon, sort)
SELECT 218, current_timestamp, current_timestamp, 0, 0, 6, 'NotifyBatchSend', '批量发送', false, 1,
       'batch-send', 'notify/views/NotifyBatchSend', 'notify:batch:send', 'Promotion', 30
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 218);

INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title,
                      menu_type, path, component, permission, icon, sort, hidden)
SELECT 219, current_timestamp, current_timestamp, 0, 0, 218, 'NotifyBatchSendAction', '批量发送操作', 2,
       '', '', 'notify:batch:send', '', 1, false
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 219);
