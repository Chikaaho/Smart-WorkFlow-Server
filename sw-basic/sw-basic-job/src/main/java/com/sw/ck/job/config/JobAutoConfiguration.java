package com.sw.ck.job.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 定时任务自动配置。
 * <p>
 * 默认关闭，通过 sw.job.enabled=true 开启。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "sw.job", name = "enabled", havingValue = "true")
public class JobAutoConfiguration {
}
