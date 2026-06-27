package com.sw.ck.notify.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 系统通知自动配置。
 * <p>
 * 默认关闭，通过 {@code sw.notify.enabled=true} 开启。
 * </p>
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "sw.notify", name = "enabled", havingValue = "true")
@MapperScan("com.sw.ck.notify.mapper")
public class NotifyAutoConfiguration {
}
