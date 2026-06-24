package com.sw.ck.form.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 低代码表单自动配置。
 * <p>
 * 默认关闭，通过 sw.form.enabled=true 开启。
 * workflow 模块依赖此配置的顺序保证（after = FormAutoConfiguration.class）。
 * </p>
 */
@AutoConfiguration
@EnableAsync
@ConditionalOnProperty(prefix = "sw.form", name = "enabled", havingValue = "true")
@MapperScan("com.sw.ck.form.mapper")
public class FormAutoConfiguration {
}
