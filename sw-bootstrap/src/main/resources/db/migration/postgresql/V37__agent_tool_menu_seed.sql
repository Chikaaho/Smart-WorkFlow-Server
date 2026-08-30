-- ===================================================================
-- V37: 智能体 → 工具管理二级菜单 + 按钮级权限（P48 / M07-F03-02）
--
-- 现场核验（非训练记忆）：
--   · V33 已在智能体目录（id=7）下挂「大模型管理」（id=209），
--     按钮级 id=210/211；V37 沿用同目录，页面 id=212，按钮 id=213。
--   · 后端权限契约：AgentToolConfigController
--     列表/详情 @ss.hasPermi('agent:tool:view')、
--     新建/编辑/删除/启停 @ss.hasPermi('agent:tool:manage')，
--     与本节菜单权限一一闭合。
--   · 不 seed sys_role_menu（沿用 V6/V26/V33 决策：超管旁路，
--     普通角色由管理员在菜单管理中自行配置）。
-- ===================================================================

-- 1. 智能体 → 工具管理（二级菜单，仿 V33 id=209 写法）
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title, hidden, menu_type, path, component, permission, icon, sort)
VALUES (212, current_timestamp, current_timestamp, 0, 0, 7, 'AgentTool', '工具管理', false, 1, 'tool', 'agent/views/ToolList', 'agent:tool:view', 'SetUp', 30);

-- 2. 智能体 → 工具管理 → 新建/编辑/删除/启停（按钮级，agent:tool:manage）
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title,
                      menu_type, path, component, permission, icon, sort, hidden)
SELECT 213, current_timestamp, current_timestamp, 0, 0, 212, 'AgentToolManage', '工具新建/编辑/删除/启停', 2, '', '', 'agent:tool:manage', '', 1, false
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 213);
