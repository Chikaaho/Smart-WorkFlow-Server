-- ===================================================================
-- Smart-WorkFlow :: Form 模块 definition 列类型修正 (H2)
-- ===================================================================
-- V41 (仅 H2)：sw_form_config.definition / sw_form_snapshot.definition
-- 由原生 JSON 类型降级为 CLOB。
--
-- 根因：dev 服务器 H2 运行在原生模式（非 MODE=PostgreSQL），
-- H2 原生 JSON 列对 JDBC setString 写入的任意文本按 JSON 标量包装，
-- 读回时字符串被额外 JSON 引号包裹，导致 definition 解析失败
--（publish 报"definition 中缺少 fields 数组"）。
-- 实体层 FormConfigEntity.definition 本就是 String（应用层 JSON 序列化），
-- CLOB 与 PostgreSQL 侧行为对齐，且与现有集成测试（PG 模式 H2）语义一致。
--
-- 注意：postgresql 链路保持原生 JSON 不变（PG 驱动隐式cast行为正常），
-- 本文件只存在于 h2 目录，不进入 postgresql 迁移链。
-- ===================================================================

ALTER TABLE sw_form_config ALTER COLUMN definition SET DATA TYPE CLOB;
ALTER TABLE sw_form_snapshot ALTER COLUMN definition SET DATA TYPE CLOB;
