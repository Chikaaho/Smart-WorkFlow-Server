package com.sw.ck.storage.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 文件存储自动配置（MinIO）。
 * <p>
 * 默认关闭，通过 sw.storage.enabled=true 开启。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "sw.storage", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(MinioProperties.class)
public class StorageAutoConfiguration {
}
