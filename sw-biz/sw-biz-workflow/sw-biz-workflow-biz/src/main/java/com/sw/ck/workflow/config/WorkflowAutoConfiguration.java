package com.sw.ck.workflow.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;

/**
 * 流程引擎自动配置（Flowable）。
 * <p>
 * 默认关闭，通过 sw.workflow.enabled=true 开启。
 * 运行时强制要求 sw.lowcode.enabled=true，否则启动失败。
 * <p>
 * @AutoConfigureAfter(name = ...) 使用全限定类名字符串，避免编译期依赖 lowcode-biz。
 */
@AutoConfiguration
@AutoConfigureAfter(name = "com.sw.ck.lowcode.config.LowcodeAutoConfiguration")
@ConditionalOnProperty(prefix = "sw.workflow", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(WorkflowProperties.class)
public class WorkflowAutoConfiguration {

    public WorkflowAutoConfiguration(Environment environment) {
        String lowcodeEnabled = environment.getProperty("sw.lowcode.enabled");
        if (!"true".equals(lowcodeEnabled)) {
            throw new IllegalStateException(
                    "工作流(sw.workflow.enabled=true)必须配合低代码表单使用，请同时设置 sw.lowcode.enabled=true");
        }
    }
}
