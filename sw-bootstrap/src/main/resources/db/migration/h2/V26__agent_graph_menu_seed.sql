-- ===================================================================
-- V26: 智能体菜单矫正为目录 + 图定义管理二级菜单（M07-F02 Step9 前端入口）
--
-- 现场核验（非训练记忆）：
--   · V6 已 seed「智能体」(id=7) 为叶子菜单（menu_type=1,
--     component='agent/views/AgentHome', permission='agent:view'）——
--     即真实 sys_menu 中「智能体」占位已存在，本迁移按方案 §3.1
--     「已有同名真实目录则复用其结构层级」处理：仿 V11 对「系统管理」(id=1)
--     的矫正先例，将 id=7 从叶子菜单矫正为目录，再挂二级「图定义管理」。
--   · 权限-菜单关联为独立关联表 sys_role_menu（V5 唯一索引
--     uk_sys_role_menu_tenant）；V6 决策「不 seed sys_role_menu
--     （超管旁路，本环既定）」沿用，本迁移同样不 seed。
--   · 不新增权限码：目录沿用 V6 既有 agent:view；二级菜单沿用 Step1/7/8
--     既有 agent:model:view（列表只读）；新建/发布/删除等按钮级操作复用
--     同一份 agent:model:manage（前端 hasPerm 控制按钮显隐）。
--   · 图设计器画布页（agent/graph-designer/:id）为参数化静态路由
--     （仿 form-designer 先例），不占菜单节点，由列表页按钮跳转进入。
-- ===================================================================

-- 1. 将「智能体」(id=7) 从叶子菜单矫正为目录（对齐 V11 对「系统管理」的矫正，
--    使子菜单在侧边栏可展开；component 置空后由子菜单自己的 component 渲染）
UPDATE sys_menu SET menu_type = 0, component = NULL WHERE id = 7;

-- 2. 智能体 → 图定义管理
--    id 使用 15（1-14 已被 V6/V10/V15 占用，15+ 空闲）
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title, hidden, menu_type, path, component, permission, icon, sort)
VALUES (15, current_timestamp, current_timestamp, 0, 0, 7, 'AgentGraphDef', '图定义管理', false, 1, 'graph-def', 'agent/views/GraphDefList', 'agent:model:view', 'Share', 10);
