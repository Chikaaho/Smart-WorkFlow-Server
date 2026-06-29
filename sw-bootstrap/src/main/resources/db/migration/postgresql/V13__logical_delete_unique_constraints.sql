-- ===================================================================
-- V13: 逻辑删除唯一约束改造 — 所有唯一索引加上 deleted 列
-- ===================================================================
-- 目的：支持逻辑删除（@TableLogic）后，以相同业务键重建记录时不撞唯一索引。
-- 背景：所有主库业务表已有 deleted 列（smallint not null default 0），
--       但现有唯一约束/唯一索引不含 deleted，导致软删后 INSERT 同名记录失败。
--
-- 方案：将所有唯一索引改为复合唯一 (..., deleted) 而非 partial WHERE deleted=0。
-- 理由：复合唯一列是 ANSI SQL 标准，PG / H2 / MySQL 通用；
--       允许 (key, deleted=0) 与 (key, deleted=1) 共存。
--
-- 改动：
--   1. sys_tenant(code)                     → UNIQUE (code, deleted)
--   2. sys_user(username)                   → UNIQUE (username, deleted)
--   3. sys_role(tenant_id, code)            → UNIQUE (tenant_id, code, deleted)
--   4. sys_user_role(tenant_id, user_id, role_id) → UNIQUE (..., deleted)
--   5. sys_role_menu(tenant_id, role_id, menu_id) → UNIQUE (..., deleted)
--   6. sys_dict_type(code)                  → UNIQUE (code, deleted)
--   7. sw_form_def(form_key)                → UNIQUE (form_key, deleted)
--   8. sw_form_config(table_name)           → UNIQUE (table_name, deleted)
--   9. wf_external_datasource(name)         → UNIQUE (name, deleted)
--
-- 无需改动的唯一索引：
--   · sw_workflow_form_binding.uk_sw_wf_binding_active
--     — 已有 WHERE active=true，不屏蔽软删重建
--
-- ❌ 不碰动态宽表（sw_form_{nanoId} / sw_form_table_{nanoId}）
-- ❌ 不碰 ACT_*（Flowable 自管）
-- ❌ 不碰扩展库表
-- ===================================================================

-- ==================== 1. sys_tenant ====================
DROP INDEX IF EXISTS uk_sys_tenant_code;
CREATE UNIQUE INDEX uk_sys_tenant_code ON sys_tenant (code, deleted);

-- ==================== 2. sys_user ====================
DROP INDEX IF EXISTS uk_sys_user_username;
CREATE UNIQUE INDEX uk_sys_user_username ON sys_user (username, deleted);

-- ==================== 3. sys_role ====================
DROP INDEX IF EXISTS uk_sys_role_tenant_code;
CREATE UNIQUE INDEX uk_sys_role_tenant_code ON sys_role (tenant_id, code, deleted);

-- ==================== 4. sys_user_role ====================
DROP INDEX IF EXISTS uk_sys_user_role_tenant;
CREATE UNIQUE INDEX uk_sys_user_role_tenant ON sys_user_role (tenant_id, user_id, role_id, deleted);

-- ==================== 5. sys_role_menu ====================
DROP INDEX IF EXISTS uk_sys_role_menu_tenant;
CREATE UNIQUE INDEX uk_sys_role_menu_tenant ON sys_role_menu (tenant_id, role_id, menu_id, deleted);

-- ==================== 6. sys_dict_type ====================
DROP INDEX IF EXISTS uk_sys_dict_type_code;
CREATE UNIQUE INDEX uk_sys_dict_type_code ON sys_dict_type (code, deleted);

-- ==================== 7. sw_form_def (form_key) ====================
-- inline UNIQUE 在 PG 中创建隐式索引 sw_form_def_form_key_key
DROP INDEX IF EXISTS sw_form_def_form_key_key;
CREATE UNIQUE INDEX uk_sw_form_def_form_key ON sw_form_def (form_key, deleted);

-- ==================== 8. sw_form_config (table_name) ====================
-- V12 用 ALTER TABLE ADD CONSTRAINT 创建的唯一约束
ALTER TABLE sw_form_config DROP CONSTRAINT IF EXISTS uk_sw_form_cfg_tname;
CREATE UNIQUE INDEX uk_sw_form_cfg_tname ON sw_form_config (table_name, deleted);

-- ==================== 9. wf_external_datasource ====================
DROP INDEX IF EXISTS uk_wf_ext_ds_name;
CREATE UNIQUE INDEX uk_wf_ext_ds_name ON wf_external_datasource (name, deleted);

-- ==================== 校验说明 ====================
-- 全部索引保持原名（uk_xxx），仅改定义（复合列加上 deleted）。
-- 版本 V13 = 当前最高 V12 + 1。
-- 排除项理由：
--   ✅ sw_workflow_form_binding.uk_sw_wf_binding_active — 已有 WHERE active=true，不冲突
--   ✅ sys_post — 无唯一约束
--   ✅ sys_dict_data — 无唯一约束（仅普通索引 idx_sys_dict_data_dict_code）
--   ✅ sys_menu — 无唯一约束
--   ✅ sys_dept — 无唯一约束
--   ✅ sw_form_snapshot — 无唯一约束
--   ✅ sw_form_trace — 无唯一约束
--   ✅ sw_notify_message — 无唯一约束
--   ✅ sw_workflow_instance — 无唯一约束
--   ✅ wf_sql_execution_audit — 无唯一约束
