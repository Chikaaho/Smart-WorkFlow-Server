-- ===================================================================
-- V46: 清理 lowcode 命名残留
--
-- 背景：lowcode 前端模块已更名为 form（组件 form/views/*），但菜单种子
-- 仍保留 lowcode 标识（V6 目录 path、V44 的子菜单 path 与 permission），
-- 导致生产路由出现 /lowcode/overview、/lowcode/form。
--
-- 约束：
--   · 仅改标识符（path/name/permission），中文标题「低代码」为产品概念名保留
--   · 幂等：WHERE 守卫旧值，重复执行无副作用
-- ===================================================================

UPDATE sys_menu SET path = 'form', name = 'Form', update_time = current_timestamp
WHERE id = 2 AND path = 'lowcode';

UPDATE sys_menu SET path = 'form/overview', name = 'FormOverview',
    permission = 'form:view', update_time = current_timestamp
WHERE id = 3 AND path = 'lowcode/overview';

UPDATE sys_menu SET path = 'form/designer', name = 'FormDesigner',
    permission = 'form:design', update_time = current_timestamp
WHERE id = 4 AND path = 'lowcode/form';
