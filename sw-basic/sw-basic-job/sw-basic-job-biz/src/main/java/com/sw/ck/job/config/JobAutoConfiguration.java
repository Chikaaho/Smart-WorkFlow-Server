package com.sw.ck.job.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/**
 * 定时任务自动配置。
 * <p>
 * 默认关闭，通过 {@code sw.job.enabled=true} 开启。
 * </p>
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "sw.job", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(JobProperties.class)
@MapperScan("com.sw.ck.job.mapper")
@ComponentScan({"com.sw.ck.job.controller", "com.sw.ck.job.service", "com.sw.ck.job.impl", "com.sw.ck.job.scheduler"})
public class JobAutoConfiguration {
}
