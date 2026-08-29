-- ===================================================================
-- V44: 流程引擎子菜单真实化（最小闭环修复 A-02）
--
-- 背景：
--   V6 将「流程引擎」seed 为叶子菜单（component=workflow/views/WorkflowHome），
--   WorkflowHome 仅是目录重定向占位（<div/>），而重定向目标 /workflow/todo
--   与流程定义页 /workflow/defs 只存在于前端 Mock 菜单种子
--   （foundation/mock/seeds.ts），真实模式无路由 → 菜单落地页白屏、
--   待办/定义页 404（验收缺口 A-02/A-03）。
--
-- 变更：
--   1. id=5「流程引擎」由叶子菜单（menu_type=1）改为目录（menu_type=0，
--      component 置 NULL），与「系统管理」「低代码」目录语义对齐；
--   2. 新增子菜单（id=20~23，挂 parent_id=5）：
--      待办任务 / 已办任务 / 流程监控 / 流程定义，component 指向
--      src/modules/workflow/views 下既有页面，经前端
--      buildRoutesFromMenu 白名单解析注册真实路由；
--   3. 幂等：UPDATE + WHERE NOT EXISTS，重复执行不产生重复行。
--

-- 6. 低代码子菜单修正（同一契约：完整相对路径 + 真实组件）
--    V6 将 overview/form 写为相对段（嵌套挂载后无法命中），且 component 指向
--    不存在的 lowcode/views/LowcodeHome|LowcodeForm（仅 Mock 菜单引用的幻影组件，
--    resolveComponent 解析失败跳过注册 → 授权目录不可落地）。统一修正为
--    完整相对路径 + form 模块真实页面：概览落地表单管理列表，表单设计落地设计器。
UPDATE sys_menu SET path = 'lowcode/overview', component = 'form/views/FormDefList',
    permission = 'lowcode:view', update_time = current_timestamp
WHERE id = 3;

UPDATE sys_menu SET path = 'lowcode/form', component = 'form/views/FormDesigner',
    permission = 'lowcode:form:design', update_time = current_timestamp
WHERE id = 4;

-- 角色授权仍由管理员通过角色菜单管理完成，本脚本不 seed sys_role_menu。
-- ===================================================================

-- 1. 流程引擎改为目录
UPDATE sys_menu SET
    menu_type = 0,
    component = NULL,
    permission = 'workflow:view',
    update_time = current_timestamp
WHERE id = 5;

-- 2. 待办任务
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title,
                      hidden, menu_type, path, component, permission, icon, sort)
SELECT 20, current_timestamp, current_timestamp, 0, 0, 5, 'WorkflowTodo', '待办任务', false, 1,
       'workflow/todo', 'workflow/views/TodoList', 'workflow:todo:view', 'Bell', 10
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 20);

-- 3. 已办任务
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title,
                      hidden, menu_type, path, component, permission, icon, sort)
SELECT 21, current_timestamp, current_timestamp, 0, 0, 5, 'WorkflowProcessed', '已办任务', false, 1,
       'workflow/processed', 'workflow/views/ProcessedList', 'workflow:processed:view', 'Finished', 20
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 21);

-- 4. 流程监控（实例列表 + 结果/流转记录）
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title,
                      hidden, menu_type, path, component, permission, icon, sort)
SELECT 22, current_timestamp, current_timestamp, 0, 0, 5, 'WorkflowInstances', '流程监控', false, 1,
       'workflow/instances', 'workflow/views/ProcessInstanceList', 'workflow:instance:view', 'Monitor', 30
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 22);

-- 5. 流程定义
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title,
                      hidden, menu_type, path, component, permission, icon, sort)
SELECT 23, current_timestamp, current_timestamp, 0, 0, 5, 'WorkflowDefs', '流程定义', false, 1,
       'workflow/defs', 'workflow/views/ProcessDefList', 'workflow:def:view', 'Setting', 40
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 23);
