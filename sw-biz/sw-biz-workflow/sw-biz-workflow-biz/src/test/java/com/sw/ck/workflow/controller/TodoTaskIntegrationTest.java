package com.sw.ck.workflow.controller;

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
import com.sw.ck.common.exception.BaseException;
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
import com.sw.ck.system.api.dict.DictItemDTO;
import com.sw.ck.workflow.dto.TodoTaskRespDTO;
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

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M04 第三环第 5 步：待办中心集成测试 — 待办查询 + 同意闭环 + 越权拒绝 + 隔离。
 * <p>
 * 端到端复用前几步链路：创建表单 → 发布 → 绑定流程 → 提交数据 → 异步发起 →
 * 查待办 → 同意 → 流程结束 → instance APPROVED。
 * </p>
 *
 * <h3>测试场景</h3>
 * <ol>
 *   <li>正常闭环：出现待办（businessKey/formKey 正确）→ complete → 流程结束 + APPROVED</li>
 *   <li>越权拒绝：非审批人调 complete → 抛异常 + task 不变 + RUNNING 不变</li>
 *   <li>越权拒绝：跨租户调 complete → 抛异常 + task 不变 + RUNNING 不变</li>
 *   <li>租户/用户隔离：另一租户/另一用户查 todo → 空列表</li>
 * </ol>
 */
@SpringBootTest(
        classes = TodoTaskIntegrationTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.flyway.enabled=false",
                "sw.workflow.enabled=true",
                "sw.form.enabled=true",
                "spring.flowable.check-process-definitions=false"
        }
)
@DisplayName("M04 第三环第 5 步：待办中心 - todo + complete 端到端")
class TodoTaskIntegrationTest {

    // ==================== 常量 ====================

    private static final Long SUPER_TENANT = 0L;
    private static final Long TENANT_A = 100L;
    private static final Long TENANT_B = 999L;
    private static final Long USER_A = 1L;
    private static final Long USER_B = 2L;
    private static final String BPMN_KEY = "skeleton_approval";
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
    private WorkflowInstanceService workflowInstanceService;

    @Autowired
    private WorkflowTodoController workflowTodoController;

    // ==================== 跟踪清理 ====================

    /** 各测试创建的动态宽表物理名 */
    private final List<String> createdTables = new ArrayList<>();

    /** 各测试创建的表单 ID */
    private final List<String> createdFormIds = new ArrayList<>();

    /** 当前测试的业务 recordId，用于清理 */
    private String currentRecordId;

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

        // — 3. 部署骨架审批 BPMN（分别为超级租户和测试租户各部署一份） —
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
        currentRecordId = null;
    }

    @AfterEach
    void tearDown() {
        testLoginContext.set(null, null);
        LoginUserHolder.clear();

        // — 清理 WorkflowInstance —
        jdbcTemplate.update("DELETE FROM sw_workflow_instance");

        // — 清理 Flowable runtime 实例 —
        List<ProcessInstance> running = runtimeService.createProcessInstanceQuery().list();
        for (ProcessInstance pi : running) {
            try {
                runtimeService.deleteProcessInstance(pi.getId(), "test cleanup");
            } catch (Exception ignored) {
            }
        }

        // — 清理 Flowable 孤立 task —
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

    // ==================== 辅助方法 ====================

    /**
     * 创建并发布一个简单表单（单个 TEXT 字段），写入跟踪列表供 @AfterEach 清理。
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

    /**
     * 轮询 Flowable task 表，等待异步 listener 完成流程发起。
     *
     * @return true=在超时前检测到 task，false=超时
     */
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

    // ==================== 测试 1：正常闭环 ====================

    @Test
    @DisplayName("提交 → 待办出现(含 businessKey/formKey) → complete → 流程结束 + APPROVED")
    void normalFlow_todoAndComplete() throws Exception {
        // === Arrange：创建表单 + 绑定 + 提交 ===
        String formKey = "todo_test_" + System.nanoTime();
        createAndPublishForm(formKey, "待办测试");
        jdbcTemplate.update("""
                INSERT INTO sw_workflow_form_binding
                    (id, form_key, process_def_key, active, tenant_id, create_by, deleted, version)
                VALUES (?, ?, ?, ?, ?, ?, 0, 0)
                """, 1000L, formKey, BPMN_KEY, true, SUPER_TENANT, SUPER_TENANT);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("field1", "todo_flow");

        String recordId = formSubmitService.submitForm(formKey, data, null, null, null);
        currentRecordId = recordId;

        // === Act 1：等待异步流程发起 ===
        boolean started = waitForProcess();
        assertThat(started)
                .as("异步 listener 应在 %d ms 内发起流程", ASYNC_TIMEOUT_MS)
                .isTrue();

        // === Assert 1：待办列表出现 ===
        List<TodoTaskRespDTO> todos = workflowTodoController.todo().getData();
        assertThat(todos)
                .as("当前用户应看到 1 个待办")
                .hasSize(1);

        TodoTaskRespDTO todo = todos.get(0);
        assertThat(todo.getTaskId())
                .as("taskId 不应为空")
                .isNotEmpty();
        assertThat(todo.getProcessInstanceId())
                .as("processInstanceId 不应为空")
                .isNotEmpty();
        assertThat(todo.getBusinessKey())
                .as("businessKey 应为提交 recordId")
                .isEqualTo(recordId);
        assertThat(todo.getFormKey())
                .as("formKey 应与表单一致")
                .isEqualTo(formKey);
        assertThat(todo.getCreateTime())
                .as("createTime 不应为空")
                .isNotNull();

        String taskId = todo.getTaskId();
        String processInstanceId = todo.getProcessInstanceId();

        // === Act 2：完成审批 ===
        workflowTodoController.complete(taskId);

        // === Assert 2：流程结束 + instance APPROVED ===
        long activeCount = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .count();
        assertThat(activeCount)
                .as("complete 后流程应结束（无活动实例）")
                .isZero();

        WorkflowInstance inst = workflowInstanceService
                .findByProcessInstanceId(processInstanceId)
                .orElseThrow(() -> new AssertionError("WorkflowInstance 应存在"));
        assertThat(inst.getStatus())
                .as("instance 状态应为 APPROVED")
                .isEqualTo(InstanceStatusEnum.APPROVED.getCode());

        // === Assert 3：待办列表已空 ===
        List<TodoTaskRespDTO> afterComplete = workflowTodoController.todo().getData();
        assertThat(afterComplete)
                .as("complete 后待办列表应为空")
                .isEmpty();

        System.out.println("=== 正常闭环验证 ===");
        System.out.println("  taskId=" + taskId + ", processInstanceId=" + processInstanceId);
        System.out.println("  businessKey=" + recordId + ", formKey=" + formKey + " ✓");
        System.out.println("  afterComplete: todoCount=" + afterComplete.size()
                + ", instanceStatus=" + inst.getStatus() + " ✓");
    }

    // ==================== 测试 2：越权拒绝 — 非审批人 ====================

    @Test
    @DisplayName("非审批人调 complete → 抛 BaseException + task 不变 + RUNNING 不变")
    void complete_withWrongAssignee_shouldThrow() throws Exception {
        // === Arrange ===
        String formKey = "auth_assignee_" + System.nanoTime();
        createAndPublishForm(formKey, "越权测试(审批人)");
        jdbcTemplate.update("""
                INSERT INTO sw_workflow_form_binding
                    (id, form_key, process_def_key, active, tenant_id, create_by, deleted, version)
                VALUES (?, ?, ?, ?, ?, ?, 0, 0)
                """, 2000L, formKey, BPMN_KEY, true, SUPER_TENANT, SUPER_TENANT);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("field1", "auth_assignee");

        formSubmitService.submitForm(formKey, data, null, null, null);
        boolean started = waitForProcess();
        assertThat(started).as("流程应在 USER_A 下发起点").isTrue();

        List<TodoTaskRespDTO> todos = workflowTodoController.todo().getData();
        assertThat(todos).as("USER_A 应看到待办").isNotEmpty();
        String taskId = todos.get(0).getTaskId();
        String processInstanceId = todos.get(0).getProcessInstanceId();

        // === Act：切换为 USER_B（同租户，不同人）===
        LoginUser userB = new LoginUser();
        userB.setUserId(USER_B);
        userB.setTenantId(TENANT_A);
        userB.setUsername("user_b");
        LoginUserHolder.set(userB);
        // TestLoginContext 保持 TENANT_A/USER_A，不影响清理

        // === Assert：抛异常 ===
        assertThatThrownBy(() -> workflowTodoController.complete(taskId))
                .as("非审批人调 complete 应抛 BaseException")
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("无权");

        // === Assert：task 仍存在（未被 complete）===
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        assertThat(task)
                .as("越权调用不应 complete task")
                .isNotNull();

        // === Assert：instance 仍 RUNNING ===
        WorkflowInstance inst = workflowInstanceService
                .findByProcessInstanceId(processInstanceId)
                .orElseThrow(() -> new AssertionError("WorkflowInstance 应存在"));
        assertThat(inst.getStatus())
                .as("越权后 status 应仍为 RUNNING")
                .isEqualTo(InstanceStatusEnum.RUNNING.getCode());

        // 还原
        LoginUser orig = new LoginUser();
        orig.setUserId(USER_A);
        orig.setTenantId(TENANT_A);
        orig.setUsername("user_a");
        LoginUserHolder.set(orig);

        System.out.println("=== 越权拒绝（审批人）验证 ===");
        System.out.println("  taskId=" + taskId + ", assignee=2 → 拒绝 ✓");
        System.out.println("  taskExists=" + (task != null)
                + ", status=" + inst.getStatus() + " ✓");
    }

    // ==================== 测试 3：越权拒绝 — 跨租户 ====================

    @Test
    @DisplayName("跨租户调 complete → 抛 BaseException + task 不变 + RUNNING 不变")
    void complete_withWrongTenant_shouldThrow() throws Exception {
        // === Arrange ===
        String formKey = "auth_tenant_" + System.nanoTime();
        createAndPublishForm(formKey, "越权测试(租户)");
        jdbcTemplate.update("""
                INSERT INTO sw_workflow_form_binding
                    (id, form_key, process_def_key, active, tenant_id, create_by, deleted, version)
                VALUES (?, ?, ?, ?, ?, ?, 0, 0)
                """, 3000L, formKey, BPMN_KEY, true, SUPER_TENANT, SUPER_TENANT);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("field1", "auth_tenant");

        formSubmitService.submitForm(formKey, data, null, null, null);
        boolean started = waitForProcess();
        assertThat(started).as("流程应在 TENANT_A 下发起").isTrue();

        List<TodoTaskRespDTO> todos = workflowTodoController.todo().getData();
        assertThat(todos).as("TENANT_A 应看到待办").isNotEmpty();
        String taskId = todos.get(0).getTaskId();
        String processInstanceId = todos.get(0).getProcessInstanceId();

        // === Act：切换为 TENANT_B（不同租户）===
        LoginUser tenantB = new LoginUser();
        tenantB.setUserId(USER_B);
        tenantB.setTenantId(TENANT_B);
        tenantB.setUsername("user_b_tenant_b");
        LoginUserHolder.set(tenantB);

        // === Assert：抛异常 ===
        assertThatThrownBy(() -> workflowTodoController.complete(taskId))
                .as("跨租户调 complete 应抛 BaseException")
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("无权");

        // === Assert：task 仍存在 ===
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        assertThat(task)
                .as("越权调用不应 complete task")
                .isNotNull();

        // === Assert：instance 仍 RUNNING ===
        WorkflowInstance inst = workflowInstanceService
                .findByProcessInstanceId(processInstanceId)
                .orElseThrow(() -> new AssertionError("WorkflowInstance 应存在"));
        assertThat(inst.getStatus())
                .as("越权后 status 应仍为 RUNNING")
                .isEqualTo(InstanceStatusEnum.RUNNING.getCode());

        // 还原
        LoginUser orig = new LoginUser();
        orig.setUserId(USER_A);
        orig.setTenantId(TENANT_A);
        orig.setUsername("user_a");
        LoginUserHolder.set(orig);

        System.out.println("=== 越权拒绝（租户）验证 ===");
        System.out.println("  taskId=" + taskId + ", tenant=999 → 拒绝 ✓");
        System.out.println("  taskExists=" + (task != null)
                + ", status=" + inst.getStatus() + " ✓");
    }

    // ==================== 测试 4：租户/用户隔离 ====================

    @Test
    @DisplayName("另一租户/另一用户查 todo → 空列表")
    void isolation_todoList_shouldNotSeeOthersTasks() throws Exception {
        // === Arrange：在 TENANT_A / USER_A 下提交 ===
        String formKey = "iso_test_" + System.nanoTime();
        createAndPublishForm(formKey, "隔离测试");
        jdbcTemplate.update("""
                INSERT INTO sw_workflow_form_binding
                    (id, form_key, process_def_key, active, tenant_id, create_by, deleted, version)
                VALUES (?, ?, ?, ?, ?, ?, 0, 0)
                """, 4000L, formKey, BPMN_KEY, true, SUPER_TENANT, SUPER_TENANT);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("field1", "isolation");

        formSubmitService.submitForm(formKey, data, null, null, null);
        boolean started = waitForProcess();
        assertThat(started).as("流程应在 TENANT_A 下发起").isTrue();

        // 确认 USER_A 能看到
        List<TodoTaskRespDTO> userATodos = workflowTodoController.todo().getData();
        assertThat(userATodos)
                .as("USER_A/TENANT_A 应看到待办")
                .hasSize(1);
        String processInstanceId = userATodos.get(0).getProcessInstanceId();

        // === Assert 1：不同租户 → 空列表 ===
        LoginUser tenantBUser = new LoginUser();
        tenantBUser.setUserId(USER_B);
        tenantBUser.setTenantId(TENANT_B);
        tenantBUser.setUsername("user_b_tenant_b");
        LoginUserHolder.set(tenantBUser);

        List<TodoTaskRespDTO> tenantBTodos = workflowTodoController.todo().getData();
        assertThat(tenantBTodos)
                .as("租户 %s 不应看到租户 %s 的待办", TENANT_B, TENANT_A)
                .isEmpty();

        // === Assert 2：同租户不同用户 → 空列表 ===
        LoginUser sameTenantDiffUser = new LoginUser();
        sameTenantDiffUser.setUserId(USER_B);
        sameTenantDiffUser.setTenantId(TENANT_A);
        sameTenantDiffUser.setUsername("user_b");
        LoginUserHolder.set(sameTenantDiffUser);

        List<TodoTaskRespDTO> userBTodos = workflowTodoController.todo().getData();
        assertThat(userBTodos)
                .as("同一租户下 USER_B 不应看到 USER_A 的待办")
                .isEmpty();

        // === Assert 3：还原后 USER_A 仍能看到（确认隔离未破坏数据）===
        LoginUser orig = new LoginUser();
        orig.setUserId(USER_A);
        orig.setTenantId(TENANT_A);
        orig.setUsername("user_a");
        LoginUserHolder.set(orig);

        List<TodoTaskRespDTO> restoredTodos = workflowTodoController.todo().getData();
        assertThat(restoredTodos)
                .as("还原上下文后 USER_A 仍应看到待办")
                .hasSize(1);

        // 仍 RUNNING
        WorkflowInstance inst = workflowInstanceService
                .findByProcessInstanceId(processInstanceId)
                .orElseThrow(() -> new AssertionError("WorkflowInstance 应存在"));
        assertThat(inst.getStatus())
                .as("未 complete 前状态应仍为 RUNNING")
                .isEqualTo(InstanceStatusEnum.RUNNING.getCode());

        System.out.println("=== 隔离验证 ===");
        System.out.println("  TENANT_B/USER_B count=" + tenantBTodos.size()
                + ", TENANT_A/USER_B count=" + userBTodos.size()
                + ", restore TENANT_A/USER_A count=" + restoredTodos.size() + " ✓");
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

    // ==================== 组合测试配置 ====================

    @Configuration
    @MapperScan({"com.sw.ck.form.mapper", "com.sw.ck.workflow.mapper"})
    @EnableAsync
    @EnableTransactionManagement
    static class TestConfig {

        // ---- DataSource ----

        @Bean
        public DataSource dataSource() {
            return DataSourceBuilder.create()
                    .url("jdbc:h2:mem:todotest;DB_CLOSE_DELAY=-1")
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
                public List<DictItemDTO> listByType(String dictType) {
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
        public com.sw.ck.workflow.listener.FormSubmittedEventListener formSubmittedEventListener(
                ProcessStartService processStartService) {
            return new com.sw.ck.workflow.listener.FormSubmittedEventListener(processStartService);
        }

        // ---- 待办控制器（被测对象）----

        @Bean
        public WorkflowTodoController workflowTodoController(
                TaskService taskService,
                RuntimeService runtimeService,
                WorkflowInstanceService workflowInstanceService,
                DomainEventPublisher domainEventPublisher) {
            return new WorkflowTodoController(taskService, runtimeService,
                    workflowInstanceService, domainEventPublisher);
        }

        // ---- MyBatis-Plus 基础设施 ----

        @Bean
        public TestLoginContext testLoginContext() {
            return new TestLoginContext();
        }

        @Bean
        public CommonMetaObjectHandler commonMetaObjectHandler(
                LoginContextProvider loginContextProvider) {
            return new CommonMetaObjectHandler(loginContextProvider);
        }

        @Bean
        public TenantProperties tenantProperties() {
            TenantProperties props = new TenantProperties();
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
            executor.setThreadNamePrefix("async-todo-");
            executor.initialize();
            return executor;
        }
    }
}
