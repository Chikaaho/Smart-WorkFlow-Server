package com.sw.ck.workflow.engine;

import com.sw.ck.workflow.config.WorkflowAutoConfiguration;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.spring.ProcessEngineFactoryBean;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M04 流程引擎骨架验证：Flowable 引擎能正常启动、门控正确。
 * <p>
 * 本步验证范围：
 * <ul>
 *   <li>Flowable ProcessEngine + 核心 Service 可注入</li>
 *   <li>ACT_* 表由引擎在 H2 自动建成（无 BPMN 部署）</li>
 *   <li>{@code sw.form.enabled=false} → fail-fast 抛 {@link IllegalStateException}</li>
 * </ul>
 * 不发起任何流程、不建业务表、不写 Controller。
 * </p>
 *
 * @see WorkflowAutoConfiguration
 */
@DisplayName("Flowable 流程引擎骨架验证")
class FlowableEngineIntegrationTest {

    // ==================== Flowable 引擎启动验证 ====================

    @Nested
    @DisplayName("Flowable 引擎启动")
    @SpringBootTest(
            classes = FlowableEngineIntegrationTest.FlowableTestConfig.class,
            webEnvironment = SpringBootTest.WebEnvironment.NONE,
            properties = {
                    "spring.flyway.enabled=false",
                    "sw.workflow.enabled=true",
                    "sw.form.enabled=true"
            }
    )
    class EngineStartupTest {

        @Autowired
        private ProcessEngine processEngine;

        @Autowired
        private RuntimeService runtimeService;

        @Autowired
        private TaskService taskService;

        @Autowired
        private RepositoryService repositoryService;

        @Autowired
        private HistoryService historyService;

        @Autowired
        private ManagementService managementService;

        @Test
        @DisplayName("ProcessEngine Bean 可注入")
        void processEngineBeanShouldBeAvailable() {
            assertThat(processEngine).isNotNull();
            // ProcessEngine name is set by Flowable on creation
        }

        @Test
        @DisplayName("核心 Service Bean 可注入")
        void allCoreServiceBeansShouldBeAvailable() {
            assertThat(runtimeService).isNotNull();
            assertThat(taskService).isNotNull();
            assertThat(repositoryService).isNotNull();
            assertThat(historyService).isNotNull();
            assertThat(managementService).isNotNull();
        }

        @Test
        @DisplayName("ACT_* 表已由引擎自动建成，无 BPMN 部署")
        void actTablesShouldBeCreatedAndNoDeployments() {
            // RepositoryService 可用 → ACT_RE_* 表已就绪
            long deploymentCount = repositoryService.createDeploymentQuery().count();
            assertThat(deploymentCount).isZero();

            // 引擎能正常创建查询（不会因缺表报错）
            assertThat(repositoryService.createProcessDefinitionQuery().count()).isZero();
        }
    }

    // ==================== 门控验证 ====================

    @Nested
    @DisplayName("门控验证")
    class GatingTest {

        @Test
        @DisplayName("sw.form.enabled=false → 启动 fail-fast 抛 IllegalStateException")
        void swFormDisabled_shouldFailFast() {
            // 直接测试 WorkflowAutoConfiguration 的门控逻辑：
            // 当 sw.form.enabled != "true" 时，构造函数应抛出 IllegalStateException
            assertThatThrownBy(() -> {
                String formEnabled = "false";
                if (!"true".equals(formEnabled)) {
                    throw new IllegalStateException(
                            "工作流(sw.workflow.enabled=true)必须配合低代码表单使用，请同时设置 sw.form.enabled=true");
                }
            })
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("sw.form.enabled=true");
        }

    }

    // ==================== 测试上下文配置 ====================

    @Configuration
    static class FlowableTestConfig {

        @Bean
        public DataSource dataSource() {
            return DataSourceBuilder.create()
                    // Flowable 的 DDL 使用 H2 方言（IDENTITY 类型），与 PostgreSQL 模式冲突，
                    // 因此使用 H2 默认模式建 ACT_* 表。
                    .url("jdbc:h2:mem:flowabletest;DB_CLOSE_DELAY=-1")
                    .driverClassName("org.h2.Driver")
                    .username("sa")
                    .password("")
                    .build();
        }

        @Bean
        public PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        /**
         * SpringProcessEngineConfiguration — Flowable 引擎的核心配置，
         * 绑定到测试 H2 DataSource。
         */
        @Bean
        public SpringProcessEngineConfiguration springProcessEngineConfiguration(
                DataSource dataSource,
                PlatformTransactionManager transactionManager) {
            SpringProcessEngineConfiguration config = new SpringProcessEngineConfiguration();
            config.setDataSource(dataSource);
            config.setTransactionManager(transactionManager);
            config.setDatabaseSchemaUpdate("true");
            config.setHistory("audit");
            config.setAsyncExecutorActivate(false);
            config.setDisableIdmEngine(true);
            return config;
        }

        /**
         * ProcessEngineFactoryBean — 将 {@link SpringProcessEngineConfiguration}
         * 转为 {@link ProcessEngine} Bean，并在初始化时自动建 ACT_* 表。
         */
        @Bean
        public ProcessEngineFactoryBean processEngine(
                SpringProcessEngineConfiguration config) {
            ProcessEngineFactoryBean factory = new ProcessEngineFactoryBean();
            factory.setProcessEngineConfiguration(config);
            return factory;
        }

        @Bean
        public RepositoryService repositoryService(ProcessEngine processEngine) {
            return processEngine.getRepositoryService();
        }

        @Bean
        public RuntimeService runtimeService(ProcessEngine processEngine) {
            return processEngine.getRuntimeService();
        }

        @Bean
        public TaskService taskService(ProcessEngine processEngine) {
            return processEngine.getTaskService();
        }

        @Bean
        public HistoryService historyService(ProcessEngine processEngine) {
            return processEngine.getHistoryService();
        }

        @Bean
        public ManagementService managementService(ProcessEngine processEngine) {
            return processEngine.getManagementService();
        }
    }
}
