-- ===================================================================
-- V5: M-Seam RBAC — 表结构调整与种子数据对齐
-- 调整 sys_role / sys_menu / sys_user_role / sys_role_menu 至 M-Seam 规范：
--   · sys_menu → 移除租户列，增加 title/hidden；继承 BaseEntityNoTenant
--   · sys_role → is_builtin→built_in(boolean), description→remark, 增加唯一(tenant_id,code)
--   · sys_user_role → 唯一约束改为 (tenant_id, user_id, role_id)
--   · sys_role_menu → 唯一约束改为 (tenant_id, role_id, menu_id)
--   · 超管角色 code 对齐 superadmin，status 对齐 CommonStatusEnum.ENABLE(=1)
-- ===================================================================

-- ==================== 1. sys_menu ====================
-- 新增 title 列，从 name 填充
ALTER TABLE sys_menu ADD COLUMN title varchar(64) NOT NULL DEFAULT '';
UPDATE sys_menu SET title = name;
ALTER TABLE sys_menu ALTER COLUMN title DROP DEFAULT;

-- 新增 hidden 列（替换 visible 语义：visible=1 → hidden=false）
ALTER TABLE sys_menu ADD COLUMN hidden boolean NOT NULL DEFAULT false;
UPDATE sys_menu SET hidden = (visible = 0);
ALTER TABLE sys_menu ALTER COLUMN hidden DROP DEFAULT;

-- 移除旧列（tenant_id → 全局表不再需要；visible/keep_alive/status/description → 新规范中移除）
ALTER TABLE sys_menu DROP COLUMN tenant_id;
ALTER TABLE sys_menu DROP COLUMN visible;
ALTER TABLE sys_menu DROP COLUMN keep_alive;
ALTER TABLE sys_menu DROP COLUMN status;
ALTER TABLE sys_menu DROP COLUMN description;

-- 调整列类型与 M-Seam 规范一致（扩宽始终安全，不会丢数据）
ALTER TABLE sys_menu ALTER COLUMN name TYPE varchar(64);
ALTER TABLE sys_menu ALTER COLUMN path TYPE varchar(128);
ALTER TABLE sys_menu ALTER COLUMN component TYPE varchar(255);
ALTER TABLE sys_menu ALTER COLUMN permission TYPE varchar(128);
ALTER TABLE sys_menu ALTER COLUMN icon TYPE varchar(64);

-- ==================== 2. sys_role ====================
-- is_builtin → built_in（类型改为 boolean）
ALTER TABLE sys_role ADD COLUMN built_in boolean NOT NULL DEFAULT false;
UPDATE sys_role SET built_in = (is_builtin = 1);
ALTER TABLE sys_role DROP COLUMN is_builtin;

-- description → remark
ALTER TABLE sys_role ADD COLUMN remark varchar(255);
UPDATE sys_role SET remark = description;
ALTER TABLE sys_role DROP COLUMN description;

-- 调整列类型
ALTER TABLE sys_role ALTER COLUMN name TYPE varchar(64);
ALTER TABLE sys_role ALTER COLUMN code TYPE varchar(64);

-- data_scope 改为可空、无默认值（S7 预留，本环不生效）
ALTER TABLE sys_role ALTER COLUMN data_scope DROP NOT NULL;
ALTER TABLE sys_role ALTER COLUMN data_scope DROP DEFAULT;

-- 唯一索引：从 (code) 改为 (tenant_id, code)
DROP INDEX IF EXISTS uk_sys_role_code;
CREATE UNIQUE INDEX uk_sys_role_tenant_code ON sys_role (tenant_id, code);

-- ==================== 3. sys_user_role ====================
-- 唯一索引：从 (user_id, role_id) 改为 (tenant_id, user_id, role_id)
DROP INDEX IF EXISTS uk_sys_user_role;
CREATE UNIQUE INDEX uk_sys_user_role_tenant ON sys_user_role (tenant_id, user_id, role_id);

-- ==================== 4. sys_role_menu ====================
-- 唯一索引：从 (role_id, menu_id) 改为 (tenant_id, role_id, menu_id)
DROP INDEX IF EXISTS uk_sys_role_menu;
CREATE UNIQUE INDEX uk_sys_role_menu_tenant ON sys_role_menu (tenant_id, role_id, menu_id);

-- ==================== 5. 种子数据对齐 ====================
-- 超管角色：code 从 admin → superadmin，status 对齐 CommonStatusEnum.ENABLE(=1)，标记内置
UPDATE sys_role SET code = 'superadmin', data_scope = NULL, status = 1 WHERE id = 1;
-- 确保超管角色标记内置（V4 插入时 is_builtin=1，V5 迁移后 built_in 为 true，此句为幂等保障）
UPDATE sys_role SET built_in = true WHERE id = 1;
