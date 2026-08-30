-- ===================================================================
-- Smart-WorkFlow :: V24: sw_agent_model_config 扩展多Key轮询/额度限流字段 (PostgreSQL)
-- M07-F01：group_key 归组 + sort 优先级 + locked_until 临时锁定 + quota_cooldown_seconds 冷却期
-- ===================================================================
ALTER TABLE sw_agent_model_config ADD COLUMN group_key VARCHAR(100);
ALTER TABLE sw_agent_model_config ADD COLUMN sort INT NOT NULL DEFAULT 0;
ALTER TABLE sw_agent_model_config ADD COLUMN locked_until TIMESTAMP;
ALTER TABLE sw_agent_model_config ADD COLUMN quota_cooldown_seconds INT NOT NULL DEFAULT 60;

CREATE INDEX idx_sw_agent_model_group ON sw_agent_model_config (tenant_id, group_key, sort);

COMMENT ON COLUMN sw_agent_model_config.group_key IS '多Key轮询候选分组标识，null=独立配置不参与轮询';
COMMENT ON COLUMN sw_agent_model_config.sort IS '组内优先级，数值越小优先级越高';
COMMENT ON COLUMN sw_agent_model_config.locked_until IS '限流临时锁定至该时间点，null或已过期=可用';
COMMENT ON COLUMN sw_agent_model_config.quota_cooldown_seconds IS '触发限流后的锁定冷却时长（秒），默认60';
