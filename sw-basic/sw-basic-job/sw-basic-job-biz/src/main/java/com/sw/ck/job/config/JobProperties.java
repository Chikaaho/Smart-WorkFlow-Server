package com.sw.ck.job.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 定时任务配置属性。
 * <p>
 * 绑定 {@code sw.job} 前缀的配置项。
 * </p>
 */
@Data
@ConfigurationProperties(prefix = "sw.job")
public class JobProperties {

    /** 是否启用定时任务模块（默认 false） */
    private boolean enabled = false;

    /** Quartz 线程池大小（默认 10） */
    private int poolSize = 10;

    /** 任务日志保留天数（默认 30，0 表示永不过期） */
    private int logRetentionDays = 30;
}
