package com.sw.ck.iot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "sw.iot.mqtt")
public class MqttProperties {

    private String url;
    private String clientId;
    private String username;
    private String password;
}
