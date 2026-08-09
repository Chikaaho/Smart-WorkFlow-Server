package com.sw.ck.agent.config;

import com.sw.ck.common.crypto.AesGcmCipher;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * 大模型接入配置管理自动配置（M07-F01 Step1）。
 * <p>
 * 功能开关模式对齐全仓库 8 模块惯例（precedent §8）：{@code sw.agent.enabled=true} 开启；
 * 装配风格对齐 {@code StorageAutoConfiguration}（@MapperScan + @ComponentScan）。
 * </p>
 * <p>
 * {@link AesGcmCipher} bean：密钥从 {@code sw.agent.cipher-key}（即环境变量
 * {@code SW_CIPHER_KEY}，与 {@code sw.external-datasource.cipher-key} 同构、共享同一把
 * 基础设施密钥）注入构造。{@code @ConditionalOnMissingBean} 保证全仓库只有一个
 * {@code AesGcmCipher} 实例（BPM 模块同款保护，precedent §1）。
 * </p>
 * <p>
 * 注意：本类为独立新建配置类，不改动 {@link AgentGraphAutoConfiguration}（LangGraph4j
 * 编排占位，留给 Step 2）。
 * </p>
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "sw.agent", name = "enabled", havingValue = "true")
@MapperScan("com.sw.ck.agent.mapper")
@ComponentScan({"com.sw.ck.agent.controller", "com.sw.ck.agent.service"})
public class AgentModelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AesGcmCipher agentAesGcmCipher(@Value("${sw.agent.cipher-key:}") String cipherKey) {
        return new AesGcmCipher(cipherKey);
    }
}
