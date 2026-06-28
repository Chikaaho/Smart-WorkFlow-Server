package com.sw.ck.workflow.listener;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw.ck.common.config.mybatis.CommonMetaObjectHandler;
import com.sw.ck.common.config.mybatis.tenant.CommonTenantLineHandler;
import com.sw.ck.common.config.mybatis.tenant.TenantProperties;
import com.sw.ck.common.datascope.DataScopeType;
import com.sw.ck.common.event.DomainEventPublisher;
import com.sw.ck.common.security.LoginContextProvider;
import com.sw.ck.form.api.dto.FormDefDTO;
import com.sw.ck.form.dynamic.DynamicTableManager;
import com.sw.ck.form.entity.FormDefEntity;
import com.sw.ck.form.entity.FormIdGenerator;
import com.sw.ck.form.mapper.FormConfigMapper;
import com.sw.ck.form.mapper.FormDefMapper;
import com.sw.ck.form.mapper.FormSnapshotMapper;
import com.sw.ck.form.mapper.FormTraceMapper;
import com.sw.ck.form.service.FormDefService;
import com.sw.ck.form.service.FormSubmitService;
import com.sw.ck.form.service.impl.FormDefServiceImpl;
import com.sw.ck.notify.api.NotifyBizType;
import com.sw.ck.notify.impl.NotifyFacadeImpl;
import com.sw.ck.notify.mapper.NotifyMessageMapper;
import com.sw.ck.notify.service.NotifyMessageService;
import com.sw.ck.notify.service.impl.NotifyMessageServiceImpl;
import com.sw.ck.security.holder.LoginUser;
import com.sw.ck.security.holder.LoginUserHolder;
import com.sw.ck.system.api.dict.DictFacade;
import com.sw.ck.system.api.dict.DictItemDTO;
import com.sw.ck.workflow.api.event.WorkflowNotifyEvent;
import com.sw.ck.workflow.api.event.WorkflowNotifyTrigger;
import com.sw.ck.workflow.entity.InstanceStatusEnum;
import com.sw.ck.workflow.entity.WorkflowInstance;
import com.sw.ck.workflow.mapper.WorkflowFormBindingMapper;
import com.sw.ck.workflow.mapper.WorkflowInstanceMapper;
import com.sw.ck.workflow.resolver.FixedApproverResolver;
import com.sw.ck.workflow.service.WorkflowFormBindingService;
import com.sw.ck.workflow.service.WorkflowInstanceService;
import com.sw.ck.workflow.service.impl.ProcessStartService;
import com.sw.ck.workflow.service.impl.WorkflowFormBindingServiceImpl;
import com.sw.ck.workflow.service.impl.WorkflowInstanceServiceImpl;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.spring.ProcessEngineFactoryBean;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M05 Step 2：流程通知链路集成测试 — TODO_CREATED + PROCESS_APPROVED。
 * <p>
 * 验证完整闭环：提交表单 → 异步发起流程 → WF_TODO 通知落库 → 完成审批 →
 * WF_APPROVED 通知落库 → 租户隔离正确。
 * </p>
 *
 * <h3>异步等待手法</h3>
 * <ul>
 *   <li>流程发起：轮询 Flowable task 表</li>
 *   <li>通知落库：轮询 sw_notify_message 表</li>
 * </ul>
 */
@SpringBootTest(
        classes = WorkflowNotifyIntegrationTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.flyway.enabled=false",
                "sw.workflow.enabled=true",
                "sw.form.enabled=true",
                "spring.flowable.check-process-definitions=false"
        }
)
@DisplayName("M05 Step 2：流程通知链路集成测试")
class WorkflowNotifyIntegrationTest {

    // ==================== 常量 ====================

    private static final Long SUPER_TENANT = 0L;
    private static final Long TENANT_A = 100L;
    private static final Long USER_A = 1L;
    private static final String BPMN_KEY = "skeleton_approval";
    private static final long ASYNC_TIMEOUT_MS = 10_000L;

    // ==================== 注入 ====================

    @Autowired
    private FormDefService formDefService;

    @Autowired
    private FormSubmitService formSubmitService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FormDefMapper formDefMapper;

    @Autowired
    private TaskService taskService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private TestLoginContext testLoginContext;

    @Autowired
    private WorkflowInstanceService workflowInstanceService;

    @Autowired
    private DomainEventPublisher domainEventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // ==================== 跟踪清理 ====================

    private final List<String> createdTables = new ArrayList<>();
    private final List<String> createdFormIds = new ArrayList<>();

    // ==================== 共享初始化 ====================

    @BeforeAll
    static void initAll(@Autowired JdbcTemplate jt,
                        @Autowired RepositoryService rs) {
        // — 1. 表单元数据表 —
        jt.execute("""
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
        jt.execute("""
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
        jt.execute("""
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
        jt.execute("""
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

        // — 2. Workflow 元数据表 —
        jt.execute("""
                CREATE TABLE IF NOT EXISTS sw_workflow_form_binding (
                    id                bigint          not null primary key,
                    create_time       timestamp       not null default current_timestamp,
                    create_by         bigint,
                    update_time       timestamp       not null default current_timestamp,
                    update_by         bigint,
                    deleted           smallint        not null default 0,
                    tenant_id         bigint          not null default 0,
                    version           bigint          not null default 0,
                    form_key          varchar(200)    not null,
                    process_def_key   varchar(200)    not null,
                    active            boolean         not null default true
                )
                """);
        jt.execute("""
                CREATE TABLE IF NOT EXISTS sw_workflow_instance (
                    id                  bigint          not null primary key,
                    create_time         timestamp       not null default current_timestamp,
                    create_by           bigint,
                    update_time         timestamp       not null default current_timestamp,
                    update_by           bigint,
                    deleted             smallint        not null default 0,
                    tenant_id           bigint          not null default 0,
                    version             bigint          not null default 0,
                    process_instance_id varchar(255)    not null,
                    process_def_key     varchar(200)    not null,
                    business_key        varchar(255),
                    form_key            varchar(200)    not null,
                    initiator_id        bigint          not null,
                    status              varchar(50)     not null default 'RUNNING'
                )
                """);

        // — 3. 通知表 —
        jt.execute("""
                CREATE TABLE IF NOT EXISTS sw_notify_message (
                    id                bigint          not null primary key,
                    create_time       timestamp       not null default current_timestamp,
                    create_by         bigint,
                    update_time       timestamp       not null default current_timestamp,
                    update_by         bigint,
                    deleted           smallint        not null default 0,
                    tenant_id         bigint          not null default 0,
                    version           bigint          not null default 0,
                    recipient_id      bigint          not null,
                    title             varchar(200)    not null,
                    content           text            not null,
                    biz_type          varchar(30)     not null,
                    biz_id            varchar(64),
                    is_read           boolean         not null default false
                )
                """);
        jt.execute("""
                CREATE INDEX IF NOT EXISTS idx_sw_notify_msg_recipient
                    ON sw_notify_message (tenant_id, recipient_id)
                """);

        // — 4. 部署骨架审批 BPMN —
        for (Long tenant : List.of(SUPER_TENANT, TENANT_A)) {
            rs.createDeployment()
                    .addClasspathResource("processes/" + BPMN_KEY + ".bpmn20.xml")
                    .tenantId(String.valueOf(tenant))
                    .name("Skeleton Approval (tenant " + tenant + ")")
                    .deploy();
        }
    }

    // ==================== 前置/后置 ====================

    @BeforeEach
    void setUp() {
        testLoginContext.set(TENANT_A, USER_A);
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(USER_A);
        loginUser.setTenantId(TENANT_A);
        loginUser.setUsername("user_a");
        LoginUserHolder.set(loginUser);
    }

    @AfterEach
    void tearDown() {
        testLoginContext.set(null, null);
        LoginUserHolder.clear();

        // — 清理通知表 —
        jdbcTemplate.update("DELETE FROM sw_notify_message");

        // — 清理 WorkflowInstance —
        jdbcTemplate.update("DELETE FROM sw_workflow_instance");

        // — 清理 Flowable runtime —
        List<ProcessInstance> running = runtimeService.createProcessInstanceQuery().list();
        for (ProcessInstance pi : running) {
            try { runtimeService.deleteProcessInstance(pi.getId(), "test cleanup"); }
            catch (Exception ignored) { }
        }
        List<Task> orphanTasks = taskService.createTaskQuery().list();
        for (Task t : orphanTasks) {
            try { taskService.deleteTask(t.getId(), true); }
            catch (Exception ignored) { }
        }

        // — 清理动态宽表 —
        for (String table : createdTables) {
            try { jdbcTemplate.execute("DROP TABLE IF EXISTS \"" + table + "\" CASCADE"); }
            catch (Exception ignored) { }
        }
        createdTables.clear();

        // — 清理表单元数据 —
        for (String fid : createdFormIds) {
            try {
                jdbcTemplate.update("DELETE FROM sw_form_trace WHERE form_id = ?", fid);
                jdbcTemplate.update("DELETE FROM sw_form_snapshot WHERE form_id = ?", fid);
                jdbcTemplate.update("DELETE FROM sw_form_config WHERE form_id = ?", fid);
                jdbcTemplate.update("DELETE FROM sw_form_def WHERE id = ?", fid);
            } catch (Exception ignored) { }
        }
        createdFormIds.clear();
    }

    // ==================== 辅助方法 ====================

    private FormDefEntity createAndPublishForm(String formKey, String formName) {
        FormDefDTO draft = formDefService.createDraft(formKey, formName, null, null);
        createdFormIds.add(draft.getId());
        formDefService.saveConfig(draft.getId(), """
                {"fields":[{"name":"field1","type":"TEXT","required":true}]}
                """);
        formDefService.publish(draft.getId());
        FormDefEntity entity = formDefMapper.selectById(draft.getId());
        createdTables.add(entity.getPhysicalTableName());
        return entity;
    }

    /** 轮询等待异步流程发起（Flowable task 出现）。 */
    private boolean waitForProcess() throws InterruptedException {
        long deadline = System.currentTimeMillis() + ASYNC_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (taskService.createTaskQuery().processDefinitionKey(BPMN_KEY).count() > 0) {
                return true;
            }
            Thread.sleep(200);
        }
        return false;
    }

    /** 轮询等待异步通知落库（按 biz_type 匹配）。 */
    private boolean waitForNotify(String bizType) throws InterruptedException {
        long deadline = System.currentTimeMillis() + ASYNC_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sw_notify_message WHERE biz_type = ?",
                    Long.class, bizType);
            if (count != null && count > 0) {
                return true;
            }
            Thread.sleep(200);
        }
        return false;
    }

    /** 查询通知表中 biz_type 对应的行（首行）。 */
    private Map<String, Object> findNotifyByBizType(String bizType) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM sw_notify_message WHERE biz_type = ? ORDER BY id ASC", bizType);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ==================== 测试 1：WF_TODO + WF_APPROVED 双链路 ====================

    @Test
    @DisplayName("提交 → WF_TODO 通知 → complete → WF_APPROVED 通知，tenant_id 正确")
    void fullNotifyChain_todoAndApproved() throws Exception {
        // === Arrange：创建表单 + 绑定 ===
        String formKey = "notify_test_" + System.nanoTime();
        createAndPublishForm(formKey, "通知链路测试");
        jdbcTemplate.update("""
                INSERT INTO sw_workflow_form_binding
                    (id, form_key, process_def_key, active, tenant_id, create_by, deleted, version)
                VALUES (?, ?, ?, ?, ?, ?, 0, 0)
                """, 5000L, formKey, BPMN_KEY, true, SUPER_TENANT, SUPER_TENANT);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("field1", "notify_chain");

        // === Act 1：提交表单 ===
        formSubmitService.submitForm(formKey, data, null, null, null);

        // === Assert 1：等待流程发起 + WF_TODO 通知落库 ===
        boolean processStarted = waitForProcess();
        assertThat(processStarted)
                .as("异步流程应在 %d ms 内发起", ASYNC_TIMEOUT_MS)
                .isTrue();

        boolean todoNotified = waitForNotify(NotifyBizType.WF_TODO.name());
        assertThat(todoNotified)
                .as("WF_TODO 通知应在 %d ms 内落库", ASYNC_TIMEOUT_MS)
                .isTrue();

        // — 获取 taskId 作为 biz_id 校验 —
        Task task = taskService.createTaskQuery()
                .processDefinitionKey(BPMN_KEY)
                .singleResult();
        assertThat(task).as("应有 1 个审批 task").isNotNull();
        String taskId = task.getId();
        String processInstanceId = task.getProcessInstanceId();

        // — WF_TODO 行校验 —
        Map<String, Object> todoMsg = findNotifyByBizType(NotifyBizType.WF_TODO.name());
        assertThat(todoMsg).as("WF_TODO 通知行应存在").isNotNull();
        // recipient = approver（FixedApproverResolver 返回 submitter=USER_A）
        assertThat(todoMsg.get("recipient_id"))
                .as("recipient 应为 approver")
                .isEqualTo(Long.valueOf(USER_A));
        assertThat(todoMsg.get("biz_id"))
                .as("biz_id 应为 taskId")
                .isEqualTo(taskId);
        assertThat(todoMsg.get("tenant_id"))
                .as("tenant_id 应由拦截器自动注入为 %s", TENANT_A)
                .isEqualTo(Long.valueOf(TENANT_A));
        assertThat(todoMsg.get("is_read"))
                .as("is_read 应为 false")
                .isEqualTo(false);
        assertThat(todoMsg.get("title"))
                .as("title 应为'您有一条待办'")
                .isEqualTo("您有一条待办");
        assertThat(todoMsg.get("content"))
                .as("content 应为待办文案")
                .isEqualTo("您有一条新的待办任务待处理");

        // === Act 2：完成审批（在同一事务内发布 APPROVED 事件）===
        taskService.complete(taskId);

        // 检测流程结束
        long activeCount = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .count();
        assertThat(activeCount).as("complete 后流程应结束").isZero();

        // 在事务内更新状态 + 发布事件（模拟 controller 的 @Transactional 行为）
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            workflowInstanceService.updateStatus(
                    processInstanceId, InstanceStatusEnum.APPROVED.getCode());

            // 查 initiator
            WorkflowInstance inst = workflowInstanceService
                    .findByProcessInstanceId(processInstanceId)
                    .orElseThrow(() -> new RuntimeException("WorkflowInstance 应存在"));

            // 发布事件（AFTER_COMMIT 将在本事务提交后触发）
            domainEventPublisher.publish(new WorkflowNotifyEvent(
                    WorkflowNotifyTrigger.PROCESS_APPROVED,
                    inst.getInitiatorId(),
                    TENANT_A,
                    USER_A,
                    processInstanceId
            ));
            return null;
        });
        // 事务提交 → AFTER_COMMIT → @Async listener 执行

        // === Assert 2：等待 WF_APPROVED 通知落库 ===
        boolean approvedNotified = waitForNotify(NotifyBizType.WF_APPROVED.name());
        assertThat(approvedNotified)
                .as("WF_APPROVED 通知应在 %d ms 内落库", ASYNC_TIMEOUT_MS)
                .isTrue();

        Map<String, Object> approvedMsg = findNotifyByBizType(NotifyBizType.WF_APPROVED.name());
        assertThat(approvedMsg).as("WF_APPROVED 通知行应存在").isNotNull();
        assertThat(approvedMsg.get("recipient_id"))
                .as("recipient 应为 initiator (=USER_A)")
                .isEqualTo(Long.valueOf(USER_A));
        assertThat(approvedMsg.get("biz_id"))
                .as("biz_id 应为 processInstanceId")
                .isEqualTo(processInstanceId);
        assertThat(approvedMsg.get("tenant_id"))
                .as("tenant_id 应由拦截器自动注入为 %s", TENANT_A)
                .isEqualTo(Long.valueOf(TENANT_A));
        assertThat(approvedMsg.get("is_read"))
                .as("is_read 应为 false")
                .isEqualTo(false);
        assertThat(approvedMsg.get("title"))
                .as("title 应为'您的申请已通过'")
                .isEqualTo("您的申请已通过");
        assertThat(approvedMsg.get("content"))
                .as("content 应为通过文案")
                .isEqualTo("您发起的申请已审批通过");

        // === Assert 3：两条通知都落库 ===
        Long totalCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sw_notify_message", Long.class);
        assertThat(totalCount).as("应恰好有 2 条通知（WF_TODO + WF_APPROVED）")
                .isEqualTo(2L);

        System.out.println("=== 通知双链路验证 ===");
        System.out.println("  taskId=" + taskId + ", piId=" + processInstanceId);
        System.out.println("  WF_TODO  : recipient=" + todoMsg.get("recipient_id")
                + ", tenant=" + todoMsg.get("tenant_id")
                + ", biz_id=" + todoMsg.get("biz_id") + " ✓");
        System.out.println("  WF_APPROVED: recipient=" + approvedMsg.get("recipient_id")
                + ", tenant=" + approvedMsg.get("tenant_id")
                + ", biz_id=" + approvedMsg.get("biz_id") + " ✓");
        System.out.println("  total=" + totalCount + " ✓");
    }

    // ==================== LoginContextProvider 测试实现 ====================

    static class TestLoginContext implements LoginContextProvider {

        private volatile Long currentUserId;
        private volatile Long currentTenantId;

        void set(Long tenantId, Long userId) {
            this.currentTenantId = tenantId;
            this.currentUserId = userId;
        }

        @Override
        public Long getUserId() { return currentUserId; }

        @Override
        public Long getTenantId() { return currentTenantId; }

        @Override
        public Long getDeptId() { return null; }

        @Override
        public DataScopeType getDataScopeType() { return DataScopeType.ALL; }

        @Override
        public Set<Long> getCustomDeptIds() { return Set.of(); }

        @Override
        public boolean isSuperAdmin() { return false; }
    }

    // ==================== 测试配置 ====================

    @Configuration
    @MapperScan({"com.sw.ck.form.mapper", "com.sw.ck.workflow.mapper", "com.sw.ck.notify.mapper"})
    @EnableAsync
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        public DataSource dataSource() {
            return DataSourceBuilder.create()
                    .url("jdbc:h2:mem:notifytest;DB_CLOSE_DELAY=-1")
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

        // ---- Flowable 引擎 ----

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

        // ---- 表单基础设施 ----

        @Bean
        public ObjectMapper objectMapper() { return new ObjectMapper(); }

        @Bean
        public FormIdGenerator formIdGenerator() { return new FormIdGenerator(); }

        @Bean
        public DynamicTableManager dynamicTableManager(JdbcTemplate jdbcTemplate) {
            return new DynamicTableManager(jdbcTemplate);
        }

        @Bean
        public DictFacade dictFacade() {
            return new DictFacade() {
                @Override
                public boolean isValidCode(String dictType, String code) { return true; }

                @Override
                public List<DictItemDTO> listByType(String dictType) { return List.of(); }

                @Override
                public String resolveLabel(String dictType, String code) { return null; }
            };
        }

        @Bean
        public DomainEventPublisher domainEventPublisher(
                org.springframework.context.ApplicationEventPublisher delegate) {
            return new DomainEventPublisher(delegate);
        }

        // ---- 表单服务 ----

        @Bean
        public FormDefService formDefService(
                FormDefMapper formDefMapper,
                FormConfigMapper formConfigMapper,
                FormSnapshotMapper formSnapshotMapper,
                DynamicTableManager dynamicTableManager,
                ObjectMapper objectMapper) {
            return new FormDefServiceImpl(formDefMapper, formConfigMapper, formSnapshotMapper,
                    dynamicTableManager, formIdGenerator(), objectMapper);
        }

        @Bean
        public FormSubmitService formSubmitService(
                FormDefMapper formDefMapper,
                FormConfigMapper formConfigMapper,
                FormTraceMapper formTraceMapper,
                DynamicTableManager dynamicTableManager,
                ObjectMapper objectMapper,
                JdbcTemplate jdbcTemplate,
                DictFacade dictFacade,
                DomainEventPublisher eventPublisher) {
            return new FormSubmitService(formDefMapper, formConfigMapper, formTraceMapper,
                    dynamicTableManager, formIdGenerator(), objectMapper, jdbcTemplate,
                    dictFacade, eventPublisher, Optional.empty());
        }

        // ---- Workflow 服务 ----

        @Bean
        public WorkflowFormBindingService workflowFormBindingService() {
            return new WorkflowFormBindingServiceImpl();
        }

        @Bean
        public WorkflowInstanceService workflowInstanceService() {
            return new WorkflowInstanceServiceImpl();
        }

        @Bean
        public FixedApproverResolver fixedApproverResolver() {
            return new FixedApproverResolver();
        }

        @Bean
        public ProcessStartService processStartService(
                WorkflowFormBindingService bindingService,
                FixedApproverResolver approverResolver,
                RuntimeService runtimeService,
                TaskService taskService,
                WorkflowInstanceService workflowInstanceService,
                DomainEventPublisher domainEventPublisher) {
            return new ProcessStartService(bindingService, approverResolver,
                    runtimeService, taskService, workflowInstanceService, domainEventPublisher);
        }

        @Bean
        public FormSubmittedEventListener formSubmittedEventListener(
                ProcessStartService processStartService) {
            return new FormSubmittedEventListener(processStartService);
        }

        // ---- 通知服务 ----

        @Bean
        public NotifyMessageService notifyMessageService() {
            return new NotifyMessageServiceImpl();
        }

        @Bean
        public com.sw.ck.notify.api.NotifyFacade notifyFacade(
                NotifyMessageService notifyMessageService) {
            return new NotifyFacadeImpl(notifyMessageService);
        }

        @Bean
        public WorkflowNotifyListener workflowNotifyListener(
                com.sw.ck.notify.api.NotifyFacade notifyFacade) {
            return new WorkflowNotifyListener(notifyFacade);
        }

        // ---- MyBatis-Plus 基础设施 ----

        @Bean
        public TestLoginContext testLoginContext() { return new TestLoginContext(); }

        @Bean
        public CommonMetaObjectHandler commonMetaObjectHandler(
                LoginContextProvider loginContextProvider) {
            return new CommonMetaObjectHandler(loginContextProvider);
        }

        @Bean
        public TenantProperties tenantProperties() {
            TenantProperties props = new TenantProperties();
            props.getIgnoreTables().addAll(List.of(
                    "sw_form_def", "sw_form_config", "sw_form_snapshot",
                    "sw_form_trace", "sw_workflow_form_binding"
            ));
            return props;
        }

        @Bean
        public TenantLineInnerInterceptor tenantLineInnerInterceptor(
                TenantProperties tenantProperties,
                LoginContextProvider loginContextProvider) {
            return new TenantLineInnerInterceptor(
                    new CommonTenantLineHandler(tenantProperties, loginContextProvider));
        }

        @Bean
        public MybatisPlusInterceptor mybatisPlusInterceptor(
                TenantLineInnerInterceptor tenantLineInnerInterceptor) {
            MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(tenantLineInnerInterceptor);
            interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
            return interceptor;
        }

        @Bean
        public org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory(
                DataSource dataSource,
                CommonMetaObjectHandler metaObjectHandler,
                MybatisPlusInterceptor interceptor) throws Exception {
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setTypeAliasesPackage(
                    "com.sw.ck.form.entity,com.sw.ck.workflow.entity,com.sw.ck.notify.entity");
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
            globalConfig.setMetaObjectHandler(metaObjectHandler);
            factory.setGlobalConfig(globalConfig);
            factory.setPlugins(interceptor);
            return factory.getObject();
        }

        // ---- 异步支持 ----

        @Bean
        public ThreadPoolTaskExecutor taskExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(2);
            executor.setMaxPoolSize(4);
            executor.setQueueCapacity(100);
            executor.setThreadNamePrefix("async-notify-");
            executor.initialize();
            return executor;
        }
    }
}
