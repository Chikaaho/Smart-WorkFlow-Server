package com.sw.ck.iot.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * IoT 接入自动配置（MQTT Paho v5）。
 * <p>
 * 默认关闭，通过 sw.iot.enabled=true 开启。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "sw.iot", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(MqttProperties.class)
public class IotAutoConfiguration {
}
