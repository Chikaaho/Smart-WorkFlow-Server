-- ===================================================================
-- V38: 消息模板表 + 通知菜单目录化（P36 / M05-F02-01）
--
-- 现场核验（非训练记忆）：
--   · V6 已 seed「通知」(id=6) 为叶子菜单（menu_type=1,
--     component='notify/views/NotifyHome', permission='notify:view'）——
--     本迁移仿 V11/V26 先例将 id=6 矫正为目录，再挂两个二级菜单
--     （收件箱 + 消息模板），原 NotifyHome 由收件箱二级菜单承载，
--     路径/component/permission 保持原值不变（不破坏既有可达性）。
--   · 菜单 id 已用至 213（V29 200-208 / V33 209-211 / V37 212-213）；
--     本迁移使用 215/216/217（214 在 V37 注释中被提及但从未占用，
--     为免歧义跳过）。
--   · 不 seed sys_role_menu（沿用 V6/V26/V33/V37 决策：超管旁路）。
--   · 表约定对齐 V9 sw_notify_message：前缀 sw_notify_、8 基列在前、
--     bigint 主键（ASSIGN_ID 雪花）、无真实外键。
--   · 租户内模板代码唯一：仿 V13 sys_role(tenant_id, code, deleted)
--     复合唯一先例，显式含 tenant_id 与 deleted（支持软删重建）。
-- 注意：本文件注释中的占位符示例使用 ＄｛var｝ 全角写法，
--       避免 Flyway placeholder 解析把 ＄{var} 当作迁移变量。
-- ===================================================================

-- ==================== 1. 消息模板表 ====================
CREATE TABLE sw_notify_template (
    id                BIGINT NOT NULL PRIMARY KEY,
    create_time       TIMESTAMP,
    create_by         VARCHAR(64),
    update_time       TIMESTAMP,
    update_by         VARCHAR(64),
    deleted           SMALLINT NOT NULL DEFAULT 0,
    tenant_id         BIGINT NOT NULL DEFAULT 0,
    version           BIGINT NOT NULL DEFAULT 0,
    template_code     VARCHAR(100) NOT NULL,
    name              VARCHAR(100) NOT NULL,
    title_template    VARCHAR(200) NOT NULL,
    content_template  TEXT NOT NULL,
    enabled           SMALLINT NOT NULL DEFAULT 1,
    remark            VARCHAR(500)
);

COMMENT ON TABLE sw_notify_template IS 'M05 消息模板（＄{var} 简单占位符渲染，P36/M05-F02-01）';
COMMENT ON COLUMN sw_notify_template.template_code IS '稳定模板代码，同租户唯一，发送与外部调用的标识';
COMMENT ON COLUMN sw_notify_template.title_template IS '标题模板，支持 ＄{var} 占位符';
COMMENT ON COLUMN sw_notify_template.content_template IS '正文模板，支持 ＄{var} 占位符';
COMMENT ON COLUMN sw_notify_template.enabled IS '1=启用 0=停用（停用不得预览/发送）';

-- 同租户模板代码唯一（含 deleted 支持软删重建，V13 先例）
CREATE UNIQUE INDEX uk_sw_notify_template_tenant_code ON sw_notify_template (tenant_id, template_code, deleted);
CREATE INDEX idx_sw_notify_template_tenant_deleted ON sw_notify_template (tenant_id, deleted);

-- ==================== 2. 「通知」叶子矫正为目录（仿 V11/V26） ====================
UPDATE sys_menu SET menu_type = 0, component = NULL WHERE id = 6;

-- ==================== 3. 通知 → 收件箱（承载原 NotifyHome） ====================
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title, hidden, menu_type, path, component, permission, icon, sort)
VALUES (215, current_timestamp, current_timestamp, 0, 0, 6, 'NotifyInbox', '收件箱', false, 1, 'inbox', 'notify/views/NotifyHome', 'notify:view', 'Bell', 10);

-- ==================== 4. 通知 → 消息模板（管理页） ====================
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title, hidden, menu_type, path, component, permission, icon, sort)
VALUES (216, current_timestamp, current_timestamp, 0, 0, 6, 'NotifyTemplate', '消息模板', false, 1, 'template', 'notify/views/NotifyTemplateList', 'notify:template:view', 'Tickets', 20);

-- ==================== 5. 消息模板按钮级权限（新建/编辑/删除/启停/预览/发送） ====================
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title,
                      menu_type, path, component, permission, icon, sort, hidden)
SELECT 217, current_timestamp, current_timestamp, 0, 0, 216, 'NotifyTemplateManage', '模板新建/编辑/删除/启停/预览/发送', 2, '', '', 'notify:template:manage', '', 1, false
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 217);
