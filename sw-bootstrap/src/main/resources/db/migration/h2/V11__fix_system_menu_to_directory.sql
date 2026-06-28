-- ===================================================================
-- V11: 将"系统管理"(id=1) 从叶子菜单矫正为目录
--
-- 使子节点"字典管理"(id=10) 在侧边栏可见。
-- V6 将 id=1 从 V2 的目录改为了菜单(menu_type=1, component='system/views/SystemHome')，
-- 导致其下子菜单无法展开。
-- 本迁移将其恢复为目录(menu_type=0, component=NULL)，
-- 子菜单的渲染交给对应叶子菜单自己的 component。
-- ===================================================================

UPDATE sys_menu SET menu_type = 0, component = NULL WHERE id = 1;
