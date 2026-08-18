-- P24/I49：普通 admin seed 与 job/storage 方法权限。
-- 历史冲突必须显式失败：条件插入会触发既有主键/唯一约束，不得静默跳过、覆盖或夺取既有角色。
INSERT INTO sys_role (id, create_time, update_time, deleted, tenant_id, version,
                      name, code, sort, status, data_scope, built_in, remark)
SELECT 2, current_timestamp, current_timestamp, 0, 0, 0,
       '管理员', 'admin', 10, 1, 0, false, 'P24 V31 conflict sentinel'
WHERE EXISTS (SELECT 1 FROM sys_role WHERE code = 'admin' AND deleted = 0)
   OR EXISTS (SELECT 1 FROM sys_role WHERE id = 2 AND deleted = 0);

INSERT INTO sys_role (id, create_time, update_time, deleted, tenant_id, version,
                      name, code, sort, status, data_scope, built_in, remark)
VALUES (2, current_timestamp, current_timestamp, 0, 0, 0,
        '管理员', 'admin', 10, 1, 0, false, '系统初始化普通管理员角色');

INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title,
                      menu_type, path, component, permission, icon, sort, hidden)
SELECT 200, current_timestamp, current_timestamp, 0, 0, 18, 'JobCreate', '任务新增', 2, '', '', 'job:create', '', 30, false
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 200);
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title,
                      menu_type, path, component, permission, icon, sort, hidden)
SELECT 201, current_timestamp, current_timestamp, 0, 0, 18, 'JobUpdate', '任务修改', 2, '', '', 'job:update', '', 31, false
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 201);
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title,
                      menu_type, path, component, permission, icon, sort, hidden)
SELECT 202, current_timestamp, current_timestamp, 0, 0, 18, 'JobDelete', '任务删除', 2, '', '', 'job:delete', '', 32, false
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 202);
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title,
                      menu_type, path, component, permission, icon, sort, hidden)
SELECT 203, current_timestamp, current_timestamp, 0, 0, 18, 'JobPause', '任务暂停', 2, '', '', 'job:pause', '', 33, false
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 203);
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title,
                      menu_type, path, component, permission, icon, sort, hidden)
SELECT 204, current_timestamp, current_timestamp, 0, 0, 18, 'JobResume', '任务恢复', 2, '', '', 'job:resume', '', 34, false
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 204);
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title,
                      menu_type, path, component, permission, icon, sort, hidden)
SELECT 205, current_timestamp, current_timestamp, 0, 0, 18, 'JobTrigger', '任务触发', 2, '', '', 'job:trigger', '', 35, false
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 205);
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title,
                      menu_type, path, component, permission, icon, sort, hidden)
SELECT 206, current_timestamp, current_timestamp, 0, 0, 16, 'StorageUpload', '文件上传', 2, '', '', 'storage:upload', '', 30, false
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 206);
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title,
                      menu_type, path, component, permission, icon, sort, hidden)
SELECT 207, current_timestamp, current_timestamp, 0, 0, 16, 'StorageDelete', '文件删除', 2, '', '', 'storage:delete', '', 31, false
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 207);
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title,
                      menu_type, path, component, permission, icon, sort, hidden)
SELECT 208, current_timestamp, current_timestamp, 0, 0, 16, 'StorageDownload', '文件下载', 2, '', '', 'storage:download', '', 32, false
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 208);

INSERT INTO sys_role_menu (id, create_time, update_time, deleted, tenant_id, version, role_id, menu_id)
SELECT COALESCE((SELECT MAX(id) FROM sys_role_menu), 0) + ROW_NUMBER() OVER (ORDER BY m.id), current_timestamp, current_timestamp, 0, 0, 0, 2, m.id
FROM sys_menu m
WHERE m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 2 AND rm.menu_id = m.id AND rm.deleted = 0)
  AND EXISTS (SELECT 1 FROM sys_role r WHERE r.id = 2 AND r.code = 'admin' AND r.deleted = 0);
