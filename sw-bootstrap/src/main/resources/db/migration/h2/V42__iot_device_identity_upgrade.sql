-- IoT 设备身份升级：从单一 deviceKey 收敛到 productId + deviceName 复合身份
-- 新增字段：product_id, device_name, tencent_status（设备表）
-- 新增字段：product_id, device_name, command_type, semantic_mode, idempotent_key, expiry_time, retry_count, last_error, tencent_request_id, client_token, device_output（命令表）

-- ============================================================
-- sw_iot_device 表升级
-- ============================================================

-- 新增 product_id 字段（腾讯云产品 ID）
ALTER TABLE sw_iot_device ADD COLUMN product_id VARCHAR(64);
UPDATE sw_iot_device SET product_id = 'unknown' WHERE product_id IS NULL;
ALTER TABLE sw_iot_device ALTER COLUMN product_id SET NOT NULL;

-- 新增 device_name 字段（腾讯云设备名称，在产品内唯一）
ALTER TABLE sw_iot_device ADD COLUMN device_name VARCHAR(128);
UPDATE sw_iot_device SET device_name = device_key WHERE device_name IS NULL;
ALTER TABLE sw_iot_device ALTER COLUMN device_name SET NOT NULL;

-- 新增 tencent_status 字段（腾讯云在线状态）
ALTER TABLE sw_iot_device ADD COLUMN tencent_status VARCHAR(32) DEFAULT 'offline';

-- 创建 product_id + device_name 唯一索引
CREATE UNIQUE INDEX IF NOT EXISTS uk_iot_device_product_device ON sw_iot_device (product_id, device_name, tenant_id);

-- ============================================================
-- sw_iot_device_command 表升级
-- ============================================================

-- 新增 product_id 字段
ALTER TABLE sw_iot_device_command ADD COLUMN product_id VARCHAR(64);
UPDATE sw_iot_device_command SET product_id = 'unknown' WHERE product_id IS NULL;
ALTER TABLE sw_iot_device_command ALTER COLUMN product_id SET NOT NULL;

-- 新增 device_name 字段
ALTER TABLE sw_iot_device_command ADD COLUMN device_name VARCHAR(128);
UPDATE sw_iot_device_command SET device_name = device_key WHERE device_name IS NULL;
ALTER TABLE sw_iot_device_command ALTER COLUMN device_name SET NOT NULL;

-- 新增 command_type 字段（PROPERTY / ACTION）
ALTER TABLE sw_iot_device_command ADD COLUMN command_type VARCHAR(32) DEFAULT 'PROPERTY';

-- 新增 semantic_mode 字段（DEFERRED / ONLINE_CONFIRM）
ALTER TABLE sw_iot_device_command ADD COLUMN semantic_mode VARCHAR(32) DEFAULT 'DEFERRED';

-- 新增 idempotent_key 字段（幂等键，防止重复发送）
ALTER TABLE sw_iot_device_command ADD COLUMN idempotent_key VARCHAR(128);

-- 新增 expiry_time 字段（命令过期时间）
ALTER TABLE sw_iot_device_command ADD COLUMN expiry_time TIMESTAMP;

-- 新增 retry_count 字段（已尝试次数）
ALTER TABLE sw_iot_device_command ADD COLUMN retry_count INT DEFAULT 0;

-- 新增 last_error 字段（最后失败原因）
ALTER TABLE sw_iot_device_command ADD COLUMN last_error VARCHAR(512);

-- 新增 tencent_request_id 字段（腾讯云 RequestId）
ALTER TABLE sw_iot_device_command ADD COLUMN tencent_request_id VARCHAR(128);

-- 新增 client_token 字段（腾讯云异步行为 ClientToken）
ALTER TABLE sw_iot_device_command ADD COLUMN client_token VARCHAR(128);

-- 新增 device_output 字段（设备输出参数）
ALTER TABLE sw_iot_device_command ADD COLUMN device_output TEXT;

-- 创建幂等键唯一索引（H2 不支持 WHERE 子句，使用普通唯一索引）
CREATE UNIQUE INDEX IF NOT EXISTS uk_iot_command_idempotent ON sw_iot_device_command (idempotent_key);

-- 创建按设备查询待发送命令的索引
CREATE INDEX IF NOT EXISTS idx_iot_command_status_device ON sw_iot_device_command (product_id, device_name, status);

-- 创建过期命令查询索引
CREATE INDEX IF NOT EXISTS idx_iot_command_expiry ON sw_iot_device_command (expiry_time, status);
