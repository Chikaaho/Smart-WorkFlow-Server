package com.sw.ck.bpm.engine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "sw.bpm")
public class BpmProperties {

    private boolean enabled = true;
}
