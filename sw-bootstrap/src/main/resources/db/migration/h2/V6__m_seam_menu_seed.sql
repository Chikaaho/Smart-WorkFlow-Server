-- ===================================================================
-- V6: M-Seam 导航菜单树 —— 替换 V2 旧占位菜单，按前端白名单 seed
--
-- 执行顺序：
--   1. 清理由 V2 旧菜单关联的 sys_role_menu 行
--   2. 删除 V2 旧菜单（id=10~16, 100~102, 110~112）
--   3. 更新 id=1（原为目录，改为菜单 + 对齐新字段）
--   4. 插入新菜单（id=2~9，共 9 节点树）
--
-- 约束：
--   · 不 seed sys_role_menu（超管旁路，本环既定）
--   · hidden 列类型为 boolean，使用 true/false 字面量
--   · component = NULL 表示目录/按钮（前端据此判定）
--   · permission 部分行为 NULL（目录/无权限标识的菜单）
-- ===================================================================

-- ==================== 1. 清理旧角色-菜单关联 ====================
DELETE FROM sys_role_menu WHERE menu_id IN (10, 11, 12, 13, 14, 15, 16, 100, 101, 102, 110, 111, 112);

-- ==================== 2. 删除 V2 旧菜单 ====================
DELETE FROM sys_menu WHERE id IN (10, 11, 12, 13, 14, 15, 16, 100, 101, 102, 110, 111, 112);

-- ==================== 3. 更新 id=1 为 System 菜单 ====================
-- 原 V2 占位为目录（menu_type=0, path='/system', component='', sort=100），
-- 改为菜单（menu_type=1）并对齐新字段规范。
UPDATE sys_menu SET
    name = 'System',
    path = 'system',
    component = 'system/views/SystemHome',
    permission = 'system:view',
    icon = 'Setting',
    sort = 10,
    menu_type = 1,
    hidden = false
WHERE id = 1;

-- ==================== 4. 插入新菜单 ====================
-- 低代码目录
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title, hidden, menu_type, path, component, permission, icon, sort)
VALUES (2, current_timestamp, current_timestamp, 0, 0, 0, 'Lowcode', '低代码', false, 0, 'lowcode', NULL, NULL, 'Grid', 20);

-- 低代码 → 低代码概览
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title, hidden, menu_type, path, component, permission, icon, sort)
VALUES (3, current_timestamp, current_timestamp, 0, 0, 2, 'LowcodeHome', '低代码概览', false, 1, 'overview', 'lowcode/views/LowcodeHome', 'lowcode:view', 'Document', 10);

-- 低代码 → 表单设计
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title, hidden, menu_type, path, component, permission, icon, sort)
VALUES (4, current_timestamp, current_timestamp, 0, 0, 2, 'LowcodeForm', '表单设计', false, 1, 'form', 'lowcode/views/LowcodeForm', 'lowcode:form:design', 'EditPen', 20);

-- 流程引擎
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title, hidden, menu_type, path, component, permission, icon, sort)
VALUES (5, current_timestamp, current_timestamp, 0, 0, 0, 'Workflow', '流程引擎', false, 1, 'workflow', 'workflow/views/WorkflowHome', 'workflow:view', 'Share', 30);

-- 通知
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title, hidden, menu_type, path, component, permission, icon, sort)
VALUES (6, current_timestamp, current_timestamp, 0, 0, 0, 'Notify', '通知', false, 1, 'notify', 'notify/views/NotifyHome', 'notify:view', 'Bell', 40);

-- 智能体
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title, hidden, menu_type, path, component, permission, icon, sort)
VALUES (7, current_timestamp, current_timestamp, 0, 0, 0, 'Agent', '智能体', false, 1, 'agent', 'agent/views/AgentHome', 'agent:view', 'MagicStick', 50);

-- 物联网
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title, hidden, menu_type, path, component, permission, icon, sort)
VALUES (8, current_timestamp, current_timestamp, 0, 0, 0, 'Iot', '物联网', false, 1, 'iot', 'iot/views/IotHome', 'iot:view', 'Cpu', 60);

-- 开放接口
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title, hidden, menu_type, path, component, permission, icon, sort)
VALUES (9, current_timestamp, current_timestamp, 0, 0, 0, 'Openapi', '开放接口', false, 1, 'openapi', 'openapi/views/OpenapiHome', 'openapi:view', 'Connection', 70);
