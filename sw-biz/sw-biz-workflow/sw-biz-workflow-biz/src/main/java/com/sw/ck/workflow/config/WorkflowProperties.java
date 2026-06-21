package com.sw.ck.workflow.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "sw.workflow")
public class WorkflowProperties {

    private boolean enabled = true;
}
