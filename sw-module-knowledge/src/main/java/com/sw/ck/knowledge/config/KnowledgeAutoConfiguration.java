package com.sw.ck.knowledge.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(KnowledgeDataSourceProperties.class)
public class KnowledgeAutoConfiguration {
}
