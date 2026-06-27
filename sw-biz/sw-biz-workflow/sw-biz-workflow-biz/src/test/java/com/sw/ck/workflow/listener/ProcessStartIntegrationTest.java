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
import com.sw.ck.security.holder.LoginUser;
import com.sw.ck.security.holder.LoginUserHolder;
import com.sw.ck.system.api.dict.DictFacade;
import com.sw.ck.workflow.entity.WorkflowInstance;
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
 * M04 第三环第 4 步：集成测试 — 表单提交 → 异步监听 → 发起流程 → 落 instance。
 * <p>
 * 验证 {@link FormSubmittedEventListener} + {@link ProcessStartService} 的完整闭环：
 * <ol>
 *   <li>正常提交 → {@code @Async + @TransactionalEventListener(AFTER_COMMIT)} 触发 → Flowable process start → 写 WorkflowInstance</li>
 *   <li>事务回滚 → AFTER_COMMIT 不触发 → 无 Flowable 实例、无 WorkflowInstance</li>
 *   <li>租户隔离 — 不同租户不可见对方流程</li>
 *   <li>无绑定 no-op — 未绑定流程的表单提交不报错、不创建流程</li>
 * </ol>
 * </p>
 *
 * <h3>异步等待手法</h3>
 * 使用轮询（{@link #waitForProcess(long)}）等待异步 listener 完成，超时则测试失败。
 * </p>
 */
@SpringBootTest(
        classes = ProcessStartIntegrationTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.flyway.enabled=false",
                "sw.workflow.enabled=true",
                "sw.form.enabled=true",
                "spring.flowable.check-process-definitions=false"
        }
)
@DisplayName("M04 第三环第 4 步：流程发起闭环集成测试")
class ProcessStartIntegrationTest {

    // ==================== 常量 ====================

    private static final Long SUPER_TENANT = 0L;
    private static final Long TENANT_A = 100L;
    private static final Long USER_A = 1L;
    private static final String BPMN_KEY = "skeleton_approval";
    private static final String BIND_FORM_KEY = "it_application";
    private static final long ASYNC_TIMEOUT_MS = 5_000L;

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
    private WorkflowInstanceMapper workflowInstanceMapper;

    @Autowired
    private WorkflowInstanceService workflowInstanceService;

    // ==================== 跟踪清理 ====================

    /** 各测试创建的动态宽表物理名，@AfterEach 中清理 */
    private final List<String> createdTables = new ArrayList<>();

    /** 各测试创建的表单 ID，@AfterEach 中清理元数据 */
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
                    id          VARCHAR(36)  PRIMARY KEY,
                    form_id     VARCHAR(36)  NOT NULL,
                    definition  CLOB         NOT NULL,
                    tenant_id   BIGINT       NOT NULL DEFAULT 0,
                    deleted     SMALLINT     NOT NULL DEFAULT 0,
                    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    create_by   BIGINT,
                    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_by   BIGINT,
                    version     BIGINT       NOT NULL DEFAULT 0
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

        // — 3. 部署骨架审批 BPMN（分别为超级租户和测试租户各部署一份，
        //    使 startProcessInstanceByKeyAndTenantId 能找到对应租户的定义） —
        for (Long tenant : List.of(SUPER_TENANT, TENANT_A)) {
            rs.createDeployment()
                    .addClasspathResource("processes/" + BPMN_KEY + ".bpmn20.xml")
                    .tenantId(String.valueOf(tenant))
                    .name("Skeleton Approval (tenant " + tenant + ")")
                    .deploy();
        }

        // — 4. 插入 it_application → skeleton_approval 绑定 —
        jt.execute("DELETE FROM sw_workflow_form_binding WHERE form_key = '" + BIND_FORM_KEY + "'");
        jt.update("""
                INSERT INTO sw_workflow_form_binding
                    (id, form_key, process_def_key, active, tenant_id, create_by, deleted, version)
                VALUES (?, ?, ?, ?, ?, ?, 0, 0)
                """,
                1L, BIND_FORM_KEY, BPMN_KEY, true, SUPER_TENANT, SUPER_TENANT);
    }

    // ==================== 前置/后置 ====================

    @BeforeEach
    void setUp() {
        // LoginContextProvider（MP 拦截器使用）
        testLoginContext.set(TENANT_A, USER_A);
        // LoginUserHolder（FormSubmitService 使用）
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(USER_A);
        loginUser.setTenantId(TENANT_A);
        loginUser.setUsername("test_user");
        LoginUserHolder.set(loginUser);
    }

    @AfterEach
    void tearDown() {
        testLoginContext.set(null, null);
        LoginUserHolder.clear();

        // — 清理 WorkflowInstance —
        jdbcTemplate.update("DELETE FROM sw_workflow_instance");

        // — 清理 Flowable runtime 实例（级联删除 task） —
        List<ProcessInstance> running = runtimeService.createProcessInstanceQuery().list();
        List<String> piIds = running.stream().map(ProcessInstance::getId).toList();
        for (String id : piIds) {
            try {
                runtimeService.deleteProcessInstance(id, "test cleanup");
            } catch (Exception ignored) {
                // 已结束或已删除的情况静默忽略
            }
        }

        // — 清理 Flowable 孤立 task（未被级联删除的残余） —
        List<Task> orphanTasks = taskService.createTaskQuery().list();
        for (Task t : orphanTasks) {
            try {
                taskService.deleteTask(t.getId(), true);
            } catch (Exception ignored) {
            }
        }

        // — 清理表单动态宽表 —
        for (String table : createdTables) {
            try {
                jdbcTemplate.execute("DROP TABLE IF EXISTS \"" + table + "\" CASCADE");
            } catch (Exception ignored) {
            }
        }
        createdTables.clear();

        // — 清理表单元数据 —
        for (String fid : createdFormIds) {
            try {
                jdbcTemplate.update("DELETE FROM sw_form_trace WHERE form_id = ?", fid);
                jdbcTemplate.update("DELETE FROM sw_form_snapshot WHERE form_id = ?", fid);
                jdbcTemplate.update("DELETE FROM sw_form_config WHERE form_id = ?", fid);
                jdbcTemplate.update("DELETE FROM sw_form_def WHERE id = ?", fid);
            } catch (Exception ignored) {
            }
        }
        createdFormIds.clear();
    }

    // ==================== 异步等待辅助 ====================

    /**
     * 轮询 Flowable task 表，等待异步 listener 完成流程发起。
     *
     * @param timeoutMs 超时毫秒
     * @return true=在超时前检测到 task，false=超时
     */
    private boolean waitForProcess(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (taskService.createTaskQuery().processDefinitionKey(BPMN_KEY).count() > 0) {
                return true;
            }
            Thread.sleep(200);
        }
        return false;
    }

    /**
     * 创建并发布一个简单表单（单个 TEXT 字段）。
     *
     * @param formKey  表单唯一标识
     * @param formName 表单显示名
     * @return 发布后的 FormDefEntity（含 physicalTableName）
     */
    private FormDefEntity createAndPublishForm(String formKey, String formName) {
        FormDefDTO draft = formDefService.createDraft(formKey, formName, null, null);
        createdFormIds.add(draft.getId());
        formDefService.saveConfig(draft.getId(), """
                {"fields":[{"name":"field1","type":"TEXT","required":true}]}
                """);
        formDefService.publish(draft.getId(), """
                [{"name":"field1","type":"TEXT"}]
                """);
        FormDefEntity entity = formDefMapper.selectById(draft.getId());
        createdTables.add(entity.getPhysicalTableName());
        return entity;
    }

    // ==================== 测试 1：正常闭环 ====================

    @Test
    @DisplayName("正常提交 → 异步发起流程 → Flowable task + WorkflowInstance 正确落库")
    void normalFlow_shouldStartProcessAndSaveInstance() throws Exception {
        // === Arrange：创建绑定表单并提交 ===
        String formKey = BIND_FORM_KEY + "_normal_" + System.nanoTime();
        createAndPublishForm(formKey, "正常流程测试");

        // 额外插入一条指向 skeleton_approval 的绑定
        jdbcTemplate.update("""
                INSERT INTO sw_workflow_form_binding
                    (id, form_key, process_def_key, active, tenant_id, create_by, deleted, version)
                VALUES (?, ?, ?, ?, ?, ?, 0, 0)
                """,
                100L, formKey, BPMN_KEY, true, SUPER_TENANT, SUPER_TENANT);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("field1", "normal_flow");

        // === Act ===
        String recordId = formSubmitService.submitForm(formKey, data, null, null, null);

        // === Assert：等待异步流程发起 ===
        boolean started = waitForProcess(ASYNC_TIMEOUT_MS);
        assertThat(started)
                .as("异步 listener 应在 %d ms 内发起流程", ASYNC_TIMEOUT_MS)
                .isTrue();

        // — Flowable task 校验 —
        Task task = taskService.createTaskQuery()
                .processDefinitionKey(BPMN_KEY)
                .singleResult();
        assertThat(task)
                .as("应有 1 个审批 task（processKey=%s）", BPMN_KEY)
                .isNotNull();
        assertThat(task.getAssignee())
                .as("task assignee 应为 resolver 返回值 = submitter")
                .isEqualTo(String.valueOf(USER_A));
        assertThat(task.getTenantId())
                .as("task tenantId 应与部署 tenantId 一致")
                .isEqualTo(String.valueOf(TENANT_A));
        // businessKey 在 ProcessInstance 级别，不在 Task 上
        ProcessInstance procInst = runtimeService.createProcessInstanceQuery()
                .processInstanceId(task.getProcessInstanceId())
                .singleResult();
        assertThat(procInst).as("应有对应的 Flowable ProcessInstance").isNotNull();
        assertThat(procInst.getBusinessKey())
                .as("businessKey 应为表单提交 recordId")
                .isEqualTo(recordId);

        // — WorkflowInstance 校验 —
        List<WorkflowInstance> instances = workflowInstanceService
                .lambdaQuery()
                .eq(WorkflowInstance::getBusinessKey, recordId)
                .list();
        assertThat(instances)
                .as("应恰好有 1 行 WorkflowInstance 对应 recordId=%s", recordId)
                .hasSize(1);

        WorkflowInstance inst = instances.get(0);
        assertThat(inst.getProcessInstanceId())
                .as("processInstanceId 不应为空")
                .isNotEmpty();
        assertThat(inst.getProcessDefKey()).isEqualTo(BPMN_KEY);
        assertThat(inst.getBusinessKey()).isEqualTo(recordId);
        assertThat(inst.getFormKey()).isEqualTo(formKey);
        assertThat(inst.getInitiatorId()).isEqualTo(USER_A);
        assertThat(inst.getStatus()).isEqualTo("RUNNING");

        // — 拦截器自动注入校验（基列由 listener 还原的上下文填充） —
        assertThat(inst.getTenantId())
                .as("tenant_id 应由拦截器自动注入为 %s", TENANT_A)
                .isEqualTo(TENANT_A);
        assertThat(inst.getCreateBy())
                .as("create_by 应由拦截器自动注入为 %s", USER_A)
                .isEqualTo(USER_A);
        assertThat(inst.getVersion())
                .as("version 应由拦截器自动注入为 0")
                .isZero();

        System.out.println("=== 正常闭环验证 ===");
        System.out.println("  taskAssignee=" + task.getAssignee()
                + ", taskTenantId=" + task.getTenantId()
                + ", businessKey=" + procInst.getBusinessKey() + " ✓");
        System.out.println("  instanceId=" + inst.getProcessInstanceId()
                + ", status=" + inst.getStatus()
                + ", tenantId=" + inst.getTenantId()
                + ", createBy=" + inst.getCreateBy() + " ✓");
    }

    // ==================== 测试 2：回滚不触发 ====================

    @Test
    @DisplayName("事务回滚 → AFTER_COMMIT 不触发 → 无流程实例、无 WorkflowInstance")
    void transactionRollback_shouldNotTriggerProcessStart() throws Exception {
        // === Arrange ===
        String formKey = BIND_FORM_KEY + "_rollback_" + System.nanoTime();
        FormDefEntity entity = createAndPublishForm(formKey, "回滚测试");

        // 额外插入绑定
        jdbcTemplate.update("""
                INSERT INTO sw_workflow_form_binding
                    (id, form_key, process_def_key, active, tenant_id, create_by, deleted, version)
                VALUES (?, ?, ?, ?, ?, ?, 0, 0)
                """,
                200L, formKey, BPMN_KEY, true, SUPER_TENANT, SUPER_TENANT);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("field1", "rollback_test");

        // === Act：在可回滚事务内提交 ===
        TransactionTemplate tt = new TransactionTemplate(
                jdbcTemplate.getDataSource().getConnection().getTransactionIsolation() != 0
                        ? null // 占位，实际用下面注入的 transactionManager
                        : null
        );
        // 使用 Autowired PlatformTransactionManager
        org.springframework.transaction.PlatformTransactionManager tm =
                new DataSourceTransactionManager(jdbcTemplate.getDataSource());
        TransactionTemplate tx = new TransactionTemplate(tm);
        tx.execute(status -> {
            try {
                formSubmitService.submitForm(formKey, data, null, null, null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            status.setRollbackOnly();
            return null;
        });

        // === Assert：AFTER_COMMIT 不应触发 ===
        // 等一小段时间确认异步 listener 未被调用
        Thread.sleep(1500);
        long taskCount = taskService.createTaskQuery().processDefinitionKey(BPMN_KEY).count();
        long instCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sw_workflow_instance", Long.class);

        assertThat(taskCount)
                .as("事务回滚后 Flowable 不应有 task")
                .isZero();
        assertThat(instCount)
                .as("事务回滚后 WorkflowInstance 不应有记录")
                .isZero();

        System.out.println("=== 回滚验证 ===");
        System.out.println("  taskCount=" + taskCount + ", instanceCount=" + instCount + " ✓");
    }

    // ==================== 测试 3：租户隔离 ====================

    @Test
    @DisplayName("不同租户不可见对方的流程 task 和 WorkflowInstance")
    void tenantIsolation_shouldPreventCrossTenantAccess() throws Exception {
        // === Arrange：在 TENANT_A 下提交 ===
        String formKey = BIND_FORM_KEY + "_iso_" + System.nanoTime();
        createAndPublishForm(formKey, "租户隔离测试");

        jdbcTemplate.update("""
                INSERT INTO sw_workflow_form_binding
                    (id, form_key, process_def_key, active, tenant_id, create_by, deleted, version)
                VALUES (?, ?, ?, ?, ?, ?, 0, 0)
                """,
                300L, formKey, BPMN_KEY, true, SUPER_TENANT, SUPER_TENANT);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("field1", "tenant_iso");

        String recordId = formSubmitService.submitForm(formKey, data, null, null, null);
        boolean started = waitForProcess(ASYNC_TIMEOUT_MS);
        assertThat(started).as("流程应在 TENANT_A 下发起").isTrue();

        // 保存正确的 processInstanceId 待验证
        Task taskInTenantA = taskService.createTaskQuery()
                .processDefinitionKey(BPMN_KEY)
                .singleResult();
        String taskProcInstId = taskInTenantA.getProcessInstanceId();

        // === Act：切换租户上下文为 TENANT_B ===
        Long TENANT_B = 999L;
        testLoginContext.set(TENANT_B, USER_A);
        // LoginUserHolder 也被清掉（模拟不同租户线程）

        // === Assert 1：Flowable 侧 — 按租户查 task ===
        List<Task> tasksInTenantB = taskService.createTaskQuery()
                .processDefinitionKey(BPMN_KEY)
                .taskTenantId(String.valueOf(TENANT_B))
                .list();
        assertThat(tasksInTenantB)
                .as("租户 %s 不应看到租户 %s 的 task", TENANT_B, TENANT_A)
                .isEmpty();

        // === Assert 2：WorkflowInstance 侧 — MP 租户拦截器应追加 tenant_id 条件 ===
        List<WorkflowInstance> instsInTenantB = workflowInstanceService
                .lambdaQuery()
                .eq(WorkflowInstance::getBusinessKey, recordId)
                .list();
        assertThat(instsInTenantB)
                .as("租户 %s 不应看到租户 %s 的 WorkflowInstance", TENANT_B, TENANT_A)
                .isEmpty();

        // 还原上下文，清理
        testLoginContext.set(TENANT_A, USER_A);
        System.out.println("=== 租户隔离验证 ===");
        System.out.println("  processInstanceId=" + taskProcInstId
                + ", tenantB_tasks=" + tasksInTenantB.size()
                + ", tenantB_instances=" + instsInTenantB.size() + " ✓");
    }

    // ==================== 测试 4：无绑定 no-op ====================

    @Test
    @DisplayName("无绑定表单提交 → 不报错、不创建流程、不落 WorkflowInstance")
    void noBinding_shouldBeNoOp() throws Exception {
        // === Arrange：创建（但不插入绑定） ===
        String formKey = "nobind_" + System.nanoTime();
        createAndPublishForm(formKey, "无绑定测试");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("field1", "no_binding");

        // === Act：提交（无对应绑定） ===
        // 不应抛异常
        formSubmitService.submitForm(formKey, data, null, null, null);

        // === Assert：等待异步处理完成（listener 会跑但查到无绑定 → no-op） ===
        Thread.sleep(2000);
        long taskCount = taskService.createTaskQuery().processDefinitionKey(BPMN_KEY).count();
        long instCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sw_workflow_instance", Long.class);

        assertThat(taskCount)
                .as("无绑定表单不应创建 Flowable task")
                .isZero();
        assertThat(instCount)
                .as("无绑定表单不应创建 WorkflowInstance")
                .isZero();

        System.out.println("=== 无绑定验证 ===");
        System.out.println("  formKey=" + formKey
                + ", taskCount=" + taskCount
                + ", instanceCount=" + instCount + " ✓");
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

    // ==================== 测试上下文集 ====================

    @Configuration
    @MapperScan({"com.sw.ck.form.mapper", "com.sw.ck.workflow.mapper"})
    @EnableAsync
    @EnableTransactionManagement
    static class TestConfig {

        // ---- DataSource ----

        @Bean
        public DataSource dataSource() {
            return DataSourceBuilder.create()
                    .url("jdbc:h2:mem:procstarttest;DB_CLOSE_DELAY=-1")
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
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        public FormIdGenerator formIdGenerator() {
            return new FormIdGenerator();
        }

        @Bean
        public DynamicTableManager dynamicTableManager(JdbcTemplate jdbcTemplate) {
            return new DynamicTableManager(jdbcTemplate);
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

        // ---- Workflow mappers (via @MapperScan) ----

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

        // ---- MyBatis-Plus 登录上下文 ----

        @Bean
        public TestLoginContext testLoginContext() {
            return new TestLoginContext();
        }

        // ---- MyBatis-Plus 基础设施 ----

        @Bean
        public CommonMetaObjectHandler commonMetaObjectHandler(
                LoginContextProvider loginContextProvider) {
            return new CommonMetaObjectHandler(loginContextProvider);
        }

        @Bean
        public TenantProperties tenantProperties() {
            TenantProperties props = new TenantProperties();
            // 表单元数据表使用 tenant_id=0（FormDefServiceImpl 硬编码），
            // 绑定表跨租户共享，均跳过租户拦截器。
            // 仅 sw_workflow_instance 由租户拦截器托管。
            props.getIgnoreTables().addAll(List.of(
                    "sw_form_def",
                    "sw_form_config",
                    "sw_form_snapshot",
                    "sw_form_trace",
                    "sw_workflow_form_binding"
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
            factory.setTypeAliasesPackage("com.sw.ck.form.entity,com.sw.ck.workflow.entity");
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
            executor.setThreadNamePrefix("async-procstart-");
            executor.initialize();
            return executor;
        }
    }
}
