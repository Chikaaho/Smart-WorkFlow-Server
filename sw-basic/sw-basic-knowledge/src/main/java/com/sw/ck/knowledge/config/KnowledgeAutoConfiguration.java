package com.sw.ck.knowledge.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 知识库自动配置（Tika/PDFBox 文档解析 + pgvector 向量存储）。
 * <p>
 * 默认关闭，通过 sw.knowledge.enabled=true 开启。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "sw.knowledge", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(KnowledgeDataSourceProperties.class)
public class KnowledgeAutoConfiguration {
}
