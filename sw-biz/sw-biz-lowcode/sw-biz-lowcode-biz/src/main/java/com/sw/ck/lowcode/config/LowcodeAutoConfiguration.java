package com.sw.ck.lowcode.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 低代码表单自动配置。
 * <p>
 * 默认关闭，通过 sw.lowcode.enabled=true 开启。
 * worklow 模块依赖此配置的顺序保证（after = LowcodeAutoConfiguration.class）。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "sw.lowcode", name = "enabled", havingValue = "true")
public class LowcodeAutoConfiguration {
}
