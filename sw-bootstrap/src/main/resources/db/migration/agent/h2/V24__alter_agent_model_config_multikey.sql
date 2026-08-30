-- ===================================================================
-- Smart-WorkFlow :: V24: sw_agent_model_config 扩展多Key轮询/额度限流字段 (H2)
-- M07-F01：group_key 归组 + sort 优先级 + locked_until 临时锁定 + quota_cooldown_seconds 冷却期
-- 向后兼容：group_key 默认 null（不参与轮询，行为与 Step1-4 完全一致）
-- ===================================================================
ALTER TABLE sw_agent_model_config ADD COLUMN group_key VARCHAR(100);
ALTER TABLE sw_agent_model_config ADD COLUMN sort INT NOT NULL DEFAULT 0;
ALTER TABLE sw_agent_model_config ADD COLUMN locked_until TIMESTAMP;
ALTER TABLE sw_agent_model_config ADD COLUMN quota_cooldown_seconds INT NOT NULL DEFAULT 60;

CREATE INDEX idx_sw_agent_model_group ON sw_agent_model_config (tenant_id, group_key, sort);
