-- ===================================================================
-- V29: job / storage 生产菜单 seed（checklist-gap-hardening 第一批）
--
-- 背景：定时任务（M10-F03-01）与文件存储（M10-F06-01）功能代码与
-- 前端页面全链完整且已 PASSED，但生产菜单树（V6/V10/V15/V26）无对应
-- 行，仅 dev:mock 的 seeds.ts 注册，正式环境无入口（known-issues
-- I43/I44）。本迁移仿 V6/V26 先例补齐，层级/权限/路径/图标与
-- seeds.ts 中 storage/job 节点结构逐一对应：
--   · storage 顶级菜单（menu_type=1，仿 V6 顶级叶子菜单行）
--   · job 顶级目录（menu_type=0，component=NULL，仿 V6「低代码」目录行）
--   · job → 任务管理 / 执行日志 二级菜单（仿 V10/V15/V26 子菜单行）
--
-- 约束：
--   · id 使用 16-19（1-15 已被 V6/V10/V15/V26 占用，16+ 空闲）
--   · 顶级 parent_id 写 0（与 V6/V15 顶级菜单行实际写法一致；
--     后端 SysMenuServiceImpl.buildTree 将 parent_id=0 与 NULL
--     同等视为根节点，toVo 亦将两者统一转为 null）
--   · component = NULL 表示目录（前端据此判定；后端 toVo 对
--     menu_type=0/2 的行同样将 component 置 null）
--   · 不 seed sys_role_menu（超管旁路，V6 决策沿用）
--   · hidden 列类型为 boolean，使用 false 字面量（V6 惯例）
-- ===================================================================

-- 1. 文件存储（顶级菜单）
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title, hidden, menu_type, path, component, permission, icon, sort)
VALUES (16, current_timestamp, current_timestamp, 0, 0, 0, 'Storage', '文件管理', false, 1, 'storage', 'storage/views/StorageList', 'storage:view', 'FolderOpened', 80);

-- 2. 定时任务（顶级目录）
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title, hidden, menu_type, path, component, permission, icon, sort)
VALUES (17, current_timestamp, current_timestamp, 0, 0, 0, 'Job', '定时任务', false, 0, 'job', NULL, 'job:view', 'Clock', 90);

-- 3. 定时任务 → 任务管理
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title, hidden, menu_type, path, component, permission, icon, sort)
VALUES (18, current_timestamp, current_timestamp, 0, 0, 17, 'JobList', '任务管理', false, 1, 'job/list', 'job/views/JobList', 'job:list', 'List', 10);

-- 4. 定时任务 → 执行日志
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title, hidden, menu_type, path, component, permission, icon, sort)
VALUES (19, current_timestamp, current_timestamp, 0, 0, 17, 'JobLog', '执行日志', false, 1, 'job/log', 'job/views/JobLog', 'job:log', 'Document', 20);
