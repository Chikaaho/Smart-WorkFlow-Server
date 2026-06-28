package com.sw.ck.workflow.listener;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw.ck.common.event.DomainEventPublisher;
import com.sw.ck.form.api.dto.FormDefDTO;
import com.sw.ck.form.api.event.FormSubmittedEvent;
import com.sw.ck.form.dynamic.DynamicTableManager;
import com.sw.ck.form.entity.*;
import com.sw.ck.form.mapper.*;
import com.sw.ck.form.service.FormDefService;
import com.sw.ck.form.service.FormSubmitService;
import com.sw.ck.form.service.impl.FormDefServiceImpl;
import com.sw.ck.security.holder.LoginUser;
import com.sw.ck.security.holder.LoginUserHolder;
import com.sw.ck.system.api.dict.DictFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 跨模块集成测试：验证 FormSubmittedEvent 从 sw-biz-form 发布后，
 * sw-biz-workflow 侧的 {@link FormSubmittedEventListener} 能在
 * {@code @Async + @TransactionalEventListener(AFTER_COMMIT)} 下正确接收。
 * <p>
 * 本步只验证事件链路，不实现流程部署/审批（后续 M04）。
 * </p>
 * <h3>测试场景</h3>
 * <ul>
 *   <li>正常提交 → AFTER_COMMIT + @Async 触发 listener，payload 正确</li>
 *   <li>事务回滚 → AFTER_COMMIT 不触发 listener（回滚保护）</li>
 * </ul>
 * <h3>异步线程安全注意事项</h3>
 * 异步线程中 {@link com.sw.ck.security.holder.LoginUserHolder} 不可用（ThreadLocal 不跨线程），
 * 因此所有上下文信息（formKey、recordId、tenantId、submitter）均从事件 payload 获取，
 * 测试中验证这些字段正确传递。记录此陷阱。
 */
@SpringBootTest(
        classes = FormSubmittedEventIntegrationTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.flyway.enabled=false",
                "flowable.async-executor-activate=false",
                "flowable.check-process-definitions=false"
        }
)
@ActiveProfiles("test")
@DisplayName("跨模块事件链路：form → workflow")
class FormSubmittedEventIntegrationTest {

    @Autowired
    private FormDefService formDefService;

    @Autowired
    private FormSubmitService formSubmitService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FormDefMapper formDefMapper;

    @Autowired
    private TestAsyncListener asyncListener;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<String> createdTables = new ArrayList<>();
    private final List<String> createdFormIds = new ArrayList<>();

    private static final Long TEST_USER_ID = 1L;
    private static final Long TEST_TENANT_ID = 100L;

    @BeforeEach
    void setUp() {
        createMetadataTables();
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(TEST_USER_ID);
        loginUser.setTenantId(TEST_TENANT_ID);
        loginUser.setUsername("test_user");
        LoginUserHolder.set(loginUser);
        asyncListener.reset();
    }

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
        asyncListener.reset();
        for (String table : createdTables) {
            try {
                jdbcTemplate.execute("DROP TABLE IF EXISTS \"" + table + "\" CASCADE");
            } catch (Exception ignored) {
            }
        }
        createdTables.clear();
        for (String formId : createdFormIds) {
            try {
                jdbcTemplate.update("DELETE FROM sw_form_trace WHERE form_id = ?", formId);
                jdbcTemplate.update("DELETE FROM sw_form_snapshot WHERE form_id = ?", formId);
                jdbcTemplate.update("DELETE FROM sw_form_config WHERE form_id = ?", formId);
                jdbcTemplate.update("DELETE FROM sw_form_def WHERE id = ?", formId);
            } catch (Exception ignored) {
            }
        }
        createdFormIds.clear();
    }

    // ==================== 测试 1：正常提交 → 异步 listener 触发 ====================

    @Test
    @DisplayName("正常提交 → AFTER_COMMIT + @Async 触发 listener，payload 正确")
    void submitForm_shouldTriggerAsyncListenerWithCorrectPayload() throws Exception {
        // —— Arrange ——
        String formKey = "async_payload_test";
        FormDefDTO draft = formDefService.createDraft(formKey, "异步事件测试", null, null);
        createdFormIds.add(draft.getId());
        formDefService.saveConfig(draft.getId(), """
                {"fields": [{"name": "field1", "type": "TEXT", "required": true}]}
                """);
        formDefService.publish(draft.getId());
        FormDefEntity entity = formDefMapper.selectById(draft.getId());
        createdTables.add(entity.getPhysicalTableName());

        // —— Act ——
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("field1", "hello_async");
        String recordId = formSubmitService.submitForm(formKey, data, null, null, null);

        // —— Assert：等待异步 listener ——
        boolean called = asyncListener.await(5, TimeUnit.SECONDS);
        assertThat(called)
                .as("@Async + @TransactionalEventListener(AFTER_COMMIT) 应在提交后触发")
                .isTrue();
        assertThat(asyncListener.count())
                .as("listener 应被调用 1 次")
                .isEqualTo(1);

        // —— Assert：payload 正确 ——
        FormSubmittedEvent event = asyncListener.getLastEvent();
        assertThat(event).isNotNull();
        assertThat(event.getFormKey()).isEqualTo(formKey);
        assertThat(event.getRecordId()).isEqualTo(recordId);
        assertThat(event.getTenantId()).isEqualTo(TEST_TENANT_ID);
        assertThat(event.getSubmitter()).isEqualTo(String.valueOf(TEST_USER_ID));
        assertThat(event.getSubmittedData()).containsEntry("field1", "hello_async");

        System.out.println("=== 异步事件链路验证 ===");
        System.out.println("  formKey=" + event.getFormKey() + ", recordId=" + event.getRecordId()
                + ", tenantId=" + event.getTenantId() + ", submitter=" + event.getSubmitter());
        System.out.println("  异步线程调用成功 ✓");
    }

    // ==================== 测试 2：事务回滚 → listener 不触发 ====================

    @Test
    @DisplayName("事务回滚 → AFTER_COMMIT 不触发 listener")
    void submitForm_withTransactionRollback_shouldNotTriggerListener() throws Exception {
        // —— Arrange ——
        String formKey = "rollback_test";
        FormDefDTO draft = formDefService.createDraft(formKey, "回滚测试", null, null);
        createdFormIds.add(draft.getId());
        formDefService.saveConfig(draft.getId(), """
                {"fields": [{"name": "field1", "type": "TEXT", "required": true}]}
                """);
        formDefService.publish(draft.getId());
        FormDefEntity entity = formDefMapper.selectById(draft.getId());
        createdTables.add(entity.getPhysicalTableName());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("field1", "rollback_test");

        // —— Act：在可回滚的事务内提交 ——
        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        tt.execute(status -> {
            try {
                formSubmitService.submitForm(formKey, data, null, null, null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            status.setRollbackOnly();
            return null;
        });

        // —— Assert：listener 不应被触发（事务已回滚，AFTER_COMMIT 未执行） ——
        boolean called = asyncListener.await(2, TimeUnit.SECONDS);
        assertThat(called)
                .as("事务回滚后 @TransactionalEventListener(AFTER_COMMIT) 不应触发")
                .isFalse();
        assertThat(asyncListener.count())
                .as("回滚场景 listener 不应被调用")
                .isZero();

        System.out.println("=== 事务回滚事件验证 ===");
        System.out.println("  事务回滚后 listener 未被触发 ✓");
    }

    // ==================== 测试辅助 ====================

    private void createMetadataTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sw_form_def (
                    id                   VARCHAR(36)  PRIMARY KEY,
                    form_key             VARCHAR(100) NOT NULL UNIQUE,
                    name                 VARCHAR(200) NOT NULL,
                    logical_table_name   VARCHAR(100),
                    status               VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
                    physical_table_name  VARCHAR(100),
                    form_version         INT          NOT NULL DEFAULT 1,
                    description          VARCHAR(500),
                    sub_table_mapping    TEXT,
                    tenant_id            BIGINT       NOT NULL DEFAULT 0,
                    deleted              SMALLINT     NOT NULL DEFAULT 0,
                    create_time          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    create_by            BIGINT,
                    update_time          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_by            BIGINT,
                    version              BIGINT       NOT NULL DEFAULT 0
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sw_form_config (
                    id           VARCHAR(36)  PRIMARY KEY,
                    form_id      VARCHAR(36)  NOT NULL,
                    table_name   VARCHAR(200),
                    parent_table VARCHAR(200),
                    definition   CLOB         NOT NULL,
                    tenant_id    BIGINT       NOT NULL DEFAULT 0,
                    deleted      SMALLINT     NOT NULL DEFAULT 0,
                    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    create_by    BIGINT,
                    update_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_by    BIGINT,
                    version      BIGINT       NOT NULL DEFAULT 0
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sw_form_snapshot (
                    id           VARCHAR(36)  PRIMARY KEY,
                    form_id      VARCHAR(36)  NOT NULL,
                    form_version INT          NOT NULL,
                    definition   CLOB         NOT NULL,
                    tenant_id    BIGINT       NOT NULL DEFAULT 0,
                    deleted      SMALLINT     NOT NULL DEFAULT 0,
                    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    create_by    BIGINT,
                    update_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_by    BIGINT,
                    version      BIGINT       NOT NULL DEFAULT 0
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sw_form_trace (
                    id                 VARCHAR(36)  PRIMARY KEY,
                    form_id            VARCHAR(36)  NOT NULL,
                    record_id          VARCHAR(36)  NOT NULL,
                    submit_user_id     BIGINT       NOT NULL,
                    submit_ip          VARCHAR(200),
                    submit_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    device_fingerprint VARCHAR(200),
                    user_agent         VARCHAR(500),
                    tenant_id          BIGINT       NOT NULL DEFAULT 0,
                    deleted            SMALLINT     NOT NULL DEFAULT 0,
                    create_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    create_by          BIGINT,
                    update_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_by          BIGINT,
                    version            BIGINT       NOT NULL DEFAULT 0
                )
                """);
    }

    // ==================== 异步事件测试监听器 ====================

    /**
     * 使用与 {@link FormSubmittedEventListener} 相同的注解组合
     *（{@code @Async + @TransactionalEventListener(AFTER_COMMIT)}），
     * 验证事件链路在异步 + 事务提交后的行为。
     * <p>
     * 两个测试共享此监听器实例，每次 {@code @BeforeEach} 调用 {@link #reset()} 重置状态。
     * </p>
     */
    static class TestAsyncListener {

        final AtomicInteger invocationCount = new AtomicInteger(0);
        final CopyOnWriteArrayList<FormSubmittedEvent> capturedEvents = new CopyOnWriteArrayList<>();
        volatile FormSubmittedEvent lastEvent;
        volatile CountDownLatch latch = new CountDownLatch(1);

        @Async
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void onFormSubmitted(FormSubmittedEvent event) {
            invocationCount.incrementAndGet();
            capturedEvents.add(event);
            lastEvent = event;
            latch.countDown();
        }

        /** 等待异步调用（最多 timeout），返回 true 表示已被调用 */
        boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }

        int count() {
            return invocationCount.get();
        }

        FormSubmittedEvent getLastEvent() {
            return lastEvent;
        }

        void reset() {
            invocationCount.set(0);
            capturedEvents.clear();
            lastEvent = null;
            latch = new CountDownLatch(1);
        }
    }

    // ==================== 测试上下文配置 ====================

    @Configuration
    @MapperScan("com.sw.ck.form.mapper")
    @EnableAsync
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        public DataSource dataSource() {
            return DataSourceBuilder.create()
                    .url("jdbc:h2:mem:eventlinktest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
                    .driverClassName("org.h2.Driver")
                    .username("sa")
                    .password("")
                    .build();
        }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        public PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        public org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setTypeAliasesPackage("com.sw.ck.form.entity");
            MybatisConfiguration ibatisConfig = new MybatisConfiguration();
            ibatisConfig.setMapUnderscoreToCamelCase(true);
            ibatisConfig.setUseGeneratedKeys(true);
            factory.setConfiguration(ibatisConfig);
            GlobalConfig globalConfig = new GlobalConfig();
            GlobalConfig.DbConfig dbConfig = new GlobalConfig.DbConfig();
            dbConfig.setLogicDeleteField("deleted");
            dbConfig.setLogicDeleteValue("1");
            dbConfig.setLogicNotDeleteValue("0");
            globalConfig.setDbConfig(dbConfig);
            factory.setGlobalConfig(globalConfig);
            MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
            factory.setPlugins(interceptor);
            return factory.getObject();
        }

        @Bean
        public DynamicTableManager dynamicTableManager(JdbcTemplate jdbcTemplate) {
            return new DynamicTableManager(jdbcTemplate);
        }

        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        public FormDefService formDefService(FormDefMapper formDefMapper,
                                              FormConfigMapper formConfigMapper,
                                              FormSnapshotMapper formSnapshotMapper,
                                              DynamicTableManager dynamicTableManager,
                                              ObjectMapper objectMapper) {
            return new FormDefServiceImpl(formDefMapper, formConfigMapper, formSnapshotMapper,
                    dynamicTableManager, new FormIdGenerator(), objectMapper);
        }

        @Bean
        public DictFacade dictFacade() {
            return new DictFacade() {
                @Override
                public boolean isValidCode(String dictType, String code) {
                    return true;
                }

                @Override
                public List<com.sw.ck.system.api.dict.DictItemDTO> listByType(String dictType) {
                    return List.of();
                }

                @Override
                public String resolveLabel(String dictType, String code) {
                    return null;
                }
            };
        }

        @Bean
        public DomainEventPublisher domainEventPublisher(
                org.springframework.context.ApplicationEventPublisher delegate) {
            return new DomainEventPublisher(delegate);
        }

        @Bean
        public FormSubmitService formSubmitService(FormDefMapper formDefMapper,
                                                    FormConfigMapper formConfigMapper,
                                                    FormTraceMapper formTraceMapper,
                                                    DynamicTableManager dynamicTableManager,
                                                    ObjectMapper objectMapper,
                                                    JdbcTemplate jdbcTemplate,
                                                    DictFacade dictFacade,
                                                    DomainEventPublisher eventPublisher) {
            return new FormSubmitService(formDefMapper, formConfigMapper, formTraceMapper,
                    dynamicTableManager, new FormIdGenerator(), objectMapper, jdbcTemplate,
                    dictFacade, eventPublisher, Optional.empty());
        }

        @Bean
        public ThreadPoolTaskExecutor taskExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(2);
            executor.setMaxPoolSize(4);
            executor.setQueueCapacity(100);
            executor.setThreadNamePrefix("async-test-");
            executor.initialize();
            return executor;
        }

        @Bean
        public TestAsyncListener testAsyncListener() {
            return new TestAsyncListener();
        }
    }
}
