package com.sw.ck.storage.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/**
 * 文件存储自动配置。
 * <p>
 * 默认关闭，通过 {@code sw.storage.enabled=true} 开启。
 * </p>
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "sw.storage", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(StorageProperties.class)
@MapperScan("com.sw.ck.storage.mapper")
@ComponentScan({"com.sw.ck.storage.provider", "com.sw.ck.storage.controller", "com.sw.ck.storage.service", "com.sw.ck.storage.impl"})
public class StorageAutoConfiguration {
}
