package com.sw.ck.notify.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 系统通知自动配置。
 * <p>
 * 默认关闭，通过 sw.notify.enabled=true 开启。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "sw.notify", name = "enabled", havingValue = "true")
public class NotifyAutoConfiguration {
}
