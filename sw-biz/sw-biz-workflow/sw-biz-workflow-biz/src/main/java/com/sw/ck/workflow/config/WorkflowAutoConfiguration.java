package com.sw.ck.workflow.config;

import com.sw.ck.common.crypto.AesGcmCipher;
import com.sw.ck.workflow.datasource.ExternalDatasourceManager;
import com.sw.ck.workflow.executor.SqlExecutor;
import com.sw.ck.workflow.service.ExternalDatasourceService;
import com.sw.ck.workflow.service.SqlExecutionAuditService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * 流程引擎自动配置（Flowable + 外部数据源执行引擎）。
 * <p>
 * 默认关闭，通过 sw.workflow.enabled=true 开启。
 * 运行时强制要求 sw.lowcode.enabled=true，否则启动失败。
 * </p>
 */
@AutoConfiguration
@AutoConfigureAfter(name = "com.sw.ck.lowcode.config.LowcodeAutoConfiguration")
@ConditionalOnProperty(prefix = "sw.workflow", name = "enabled", havingValue = "true")
@EnableConfigurationProperties({WorkflowProperties.class, ExternalDatasourceProperties.class})
public class WorkflowAutoConfiguration {

    public WorkflowAutoConfiguration(Environment environment) {
        String lowcodeEnabled = environment.getProperty("sw.lowcode.enabled");
        if (!"true".equals(lowcodeEnabled)) {
            throw new IllegalStateException(
                    "工作流(sw.workflow.enabled=true)必须配合低代码表单使用，请同时设置 sw.lowcode.enabled=true");
        }
    }

    /**
     * AES-256-GCM 密码加密器，密钥从 {@link ExternalDatasourceProperties#getCipherKey()} 注入。
     */
    @Bean
    @ConditionalOnMissingBean
    public AesGcmCipher aesGcmCipher(ExternalDatasourceProperties properties) {
        return new AesGcmCipher(properties.getCipherKey());
    }

    /**
     * 外部数据源连接池管理器（独立于主库 dynamic-datasource）。
     */
    @Bean
    @ConditionalOnMissingBean
    public ExternalDatasourceManager externalDatasourceManager(AesGcmCipher cipher,
                                                                ExternalDatasourceProperties properties) {
        return new ExternalDatasourceManager(cipher, properties);
    }

    /**
     * SQL 执行引擎（独立 JDBC 通道，不复用主库 MP/SqlSessionFactory 拦截器）。
     */
    @Bean
    @ConditionalOnMissingBean
    public SqlExecutor sqlExecutor(ExternalDatasourceService datasourceService,
                                   ExternalDatasourceManager poolManager,
                                   SqlExecutionAuditService auditService,
                                   ExternalDatasourceProperties properties) {
        return new SqlExecutor(datasourceService, poolManager, auditService, properties);
    }
}
