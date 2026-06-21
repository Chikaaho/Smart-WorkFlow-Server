package com.sw.ck.agent.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * AI 智能助手自动配置（LangGraph4j 调度图编排）。
 * <p>
 * 默认关闭，通过 sw.agent.enabled=true 开启。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "sw.agent", name = "enabled", havingValue = "true")
public class AgentGraphAutoConfiguration {
}
