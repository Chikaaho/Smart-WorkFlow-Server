package com.sw.ck.workflow.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WorkflowProperties.class)
public class WorkflowAutoConfiguration {
}
