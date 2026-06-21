package com.sw.ck.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "sw.knowledge.datasource")
public class KnowledgeDataSourceProperties {

    private String url;
    private String username;
    private String password;
}
