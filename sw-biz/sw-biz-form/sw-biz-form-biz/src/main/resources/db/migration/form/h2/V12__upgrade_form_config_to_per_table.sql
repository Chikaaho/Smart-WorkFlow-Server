-- ===================================================================
-- Smart-WorkFlow :: Form 模块样式元数据表升级 (H2)
-- ===================================================================
-- V12: sw_form_config 从 "per form_id 一行" 升级到 "per physical table 一行"
--
-- 变更：
--   1. 新增 table_name 列（每个物理表唯一标识）
--   2. 新增 parent_table 列（子表指向父主表单 table_name）
--   3. table_name 唯一索引（NULL 不冲突）
--   4. parent_table 普通索引（查主表单的子表 WHERE parent_table = ?）
--
-- 注意：动态宽表（sw_form_{nanoId} / sw_form_table_{nanoId}）不在此处，
-- 由 DynamicTableManager 按 §6.2 例外管理。
-- ===================================================================

-- ==================== 1. 扩展 sw_form_config ====================
ALTER TABLE sw_form_config ADD COLUMN table_name   VARCHAR(200);
ALTER TABLE sw_form_config ADD COLUMN parent_table VARCHAR(200);

-- table_name 唯一（NULL 不冲突，符合 SQL 标准）
ALTER TABLE sw_form_config ADD CONSTRAINT uk_sw_form_cfg_tname UNIQUE (table_name);

-- parent_table 查子表用
CREATE INDEX idx_sw_form_cfg_parent ON sw_form_config(parent_table);

COMMENT ON COLUMN sw_form_config.table_name   IS '物理表名（唯一 key，主表单/子表各占一行）';
COMMENT ON COLUMN sw_form_config.parent_table IS '父表 table_name（子表行填写，主表单/被引用表单留空）';
