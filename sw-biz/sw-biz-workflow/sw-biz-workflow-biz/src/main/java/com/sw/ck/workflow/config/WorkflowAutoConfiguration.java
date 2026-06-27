package com.sw.ck.workflow.config;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.sw.ck.common.crypto.AesGcmCipher;
import com.sw.ck.workflow.datasource.ExternalDatasourceManager;
import com.sw.ck.workflow.executor.SqlExecutor;
import com.sw.ck.workflow.service.ExternalDatasourceService;
import com.sw.ck.workflow.service.SqlExecutionAuditService;
import org.flowable.spring.boot.ProcessEngineConfigurationConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;

/**
 * 流程引擎自动配置（Flowable + 外部数据源执行引擎）。
 * <p>
 * 默认关闭，通过 sw.workflow.enabled=true 开启。
 * 运行时强制要求 sw.form.enabled=true，否则启动失败。
 * </p>
 */
@AutoConfiguration
@AutoConfigureAfter(name = "com.sw.ck.form.config.FormAutoConfiguration")
@ConditionalOnProperty(prefix = "sw.workflow", name = "enabled", havingValue = "true")
@EnableConfigurationProperties({WorkflowProperties.class, ExternalDatasourceProperties.class})
public class WorkflowAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WorkflowAutoConfiguration.class);

    public WorkflowAutoConfiguration(Environment environment) {
        String formEnabled = environment.getProperty("sw.form.enabled");
        if (!"true".equals(formEnabled)) {
            throw new IllegalStateException(
                    "工作流(sw.workflow.enabled=true)必须配合低代码表单使用，请同时设置 sw.form.enabled=true");
        }
        log.info("Workflow engine auto-configuration started (Flowable process engine)");
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

    /**
     * Flowable 引擎绑定主库 master DataSource。
     * <p>
     * 当 dynamic-datasource 启用时（主应用），从 {@link DynamicRoutingDataSource} 提取物理 master
     * DataSource 注入 Flowable，避免 {@code @DS} 线程上下文污染引擎连接。
     * 当 dynamic-datasource 不存在时（如单元测试使用独立 H2），保持 Flowable 默认 DataSource 不变。
     * </p>
     */
    @Bean
    public ProcessEngineConfigurationConfigurer masterDataSourceBinding(
            ObjectProvider<DataSource> dataSourceProvider) {
        return config -> {
            DataSource ds = dataSourceProvider.getIfUnique();
            if (ds instanceof DynamicRoutingDataSource drds) {
                DataSource masterDs = drds.getDataSource("master");
                if (masterDs != null) {
                    config.setDataSource(masterDs);
                    log.info("Flowable engine bound to master DataSource (extracted from DynamicRoutingDataSource)");
                }
            } else {
                log.info("Flowable engine using application DataSource directly: {}",
                        ds != null ? ds.getClass().getSimpleName() : "null");
            }
        };
    }
}
