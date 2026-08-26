-- ===================================================================
-- V38: 消息模板表 + 通知菜单目录化（P36 / M05-F02-01）— H2 方言
--
-- 与 postgresql/V38 互为镜像（V9/V13/V26/V37 双方言惯例）。
-- H2 注意：无 COMMENT ON 支持。
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

-- ==================== 5. 消息模板按钮级权限 ====================
INSERT INTO sys_menu (id, create_time, update_time, deleted, version, parent_id, name, title,
                      menu_type, path, component, permission, icon, sort, hidden)
SELECT 217, current_timestamp, current_timestamp, 0, 0, 216, 'NotifyTemplateManage', '模板新建/编辑/删除/启停/预览/发送', 2, '', '', 'notify:template:manage', '', 1, false
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 217);
