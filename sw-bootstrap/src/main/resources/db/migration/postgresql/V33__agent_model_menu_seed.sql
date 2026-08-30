-- ===================================================================
-- V33: 智能体 → 大模型管理二级菜单 + 按钮级权限（P5 / M07-F01）
--
-- 现场核验（非训练记忆）：
--   · 菜单可达性审计：V26 仅挂「图定义管理」(id=15, permission=agent:model:view)，
--     「智能体」(id=7) 目录下无「大模型管理」菜单；agent:model:manage /
--     agent:model:test 两个按钮权限无任何 seed（grep 全迁移目录仅 V26 注释提及，
--     无 menu_type=2 行），生产环境无入口 → 本迁移为最小可达性 seed。
--   · 后端权限契约（现场）：AgentModelController 列表/详情 @ss.hasPermi
--     ('agent:model:view')、新建/编辑/删除 ('agent:model:manage')、
--     连通性测试 ('agent:model:test')，与本节菜单权限一一闭合。
--   · 按钮级 seed 先例：V31 以 menu_type=2 + path/component='' 的形态为
--     job/storage 补 9 枚方法权限按钮（id=200-208），本迁移沿用该形态。
--   · 不 seed sys_role_menu（V6/V26 决策「不自动授予普通角色，超管旁路」沿用；
--     普通 admin 角色是否授按钮权限由管理员在菜单管理中自行配置）。
--   · 字段清单与 V6/V10/V15/V26/V29 完全一致（含 hidden 列，V5 起存在）。
-- ===================================================================

-- 1. 智能体 → 大模型管理（二级菜单，仿 V26 id=15 图定义管理写法）
--    id 使用 209（1-208 已被 V6/V10/V15/V26/V29/V31 占用；V2 旧 10-16/100-112
--    已被 V6 DELETE 清理，V31 按钮从 200 起，209 为空闲最小值）
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title, hidden, menu_type, path, component, permission, icon, sort)
VALUES (209, current_timestamp, current_timestamp, 0, 0, 7, 'AgentModel', '大模型管理', false, 1, 'model', 'agent/views/ModelList', 'agent:model:view', 'Cpu', 20);

-- 2. 智能体 → 大模型管理 → 新建/编辑/删除（按钮级，复用 agent:model:manage）
--    字段清单与列顺序对齐 V31 按钮行先例（hidden 在列清单末尾）
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title,
                      menu_type, path, component, permission, icon, sort, hidden)
SELECT 210, current_timestamp, current_timestamp, 0, 0, 209, 'AgentModelManage', '模型新建/编辑/删除', 2, '', '', 'agent:model:manage', '', 30, false
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 210);

-- 3. 智能体 → 大模型管理 → 连通性测试（按钮级，agent:model:test）
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title,
                      menu_type, path, component, permission, icon, sort, hidden)
SELECT 211, current_timestamp, current_timestamp, 0, 0, 209, 'AgentModelTest', '模型连通性测试', 2, '', '', 'agent:model:test', '', 31, false
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 211);
