package com.sw.ck.iot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 腾讯云 IoT Explorer 配置属性。
 * <p>
 * SecretId/SecretKey 只能来自安全配置或环境变量，禁止进入数据库、普通日志、回执和前端。
 * </p>
 */
@Data
@ConfigurationProperties(prefix = "sw.iot.tencent")
public class TencentCloudProperties {

    /**
     * 是否启用腾讯云 IoT Provider（true: 使用腾讯 SDK；false: 使用 Mock Provider）。
     */
    private boolean enabled = false;

    /**
     * 腾讯云地域（如 ap-guangzhou）。
     */
    private String region = "ap-guangzhou";

    /**
     * 云 API endpoint。
     */
    private String endpoint = "iotexplorer.tencentcloudapi.com";

    /**
     * 腾讯云 SecretId（从环境变量注入，禁止硬编码）。
     */
    private String secretId;

    /**
     * 腾讯云 SecretKey（从环境变量注入，禁止硬编码）。
     */
    private String secretKey;

    /**
     * 命令队列过期时间（分钟）。
     */
    private int queueExpiryMinutes = 1440; // 24 小时

    /**
     * 最大重试次数。
     */
    private int maxRetryCount = 3;

    /**
     * Provider 模式：mock / tencent。
     */
    private String providerMode = "mock";

    /**
     * 检查腾讯云凭证是否已配置。
     */
    public boolean hasCredentials() {
        return secretId != null && !secretId.isBlank()
                && secretKey != null && !secretKey.isBlank();
    }

    /**
     * 检查是否应使用腾讯 Provider。
     */
    public boolean shouldUseTencent() {
        return "tencent".equals(providerMode) && hasCredentials();
    }
}
