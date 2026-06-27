package com.sw.ck.workflow.runner;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.sw.ck.common.config.mybatis.CommonMetaObjectHandler;
import com.sw.ck.common.config.mybatis.tenant.CommonTenantLineHandler;
import com.sw.ck.common.config.mybatis.tenant.TenantProperties;
import com.sw.ck.common.security.LoginContextProvider;
import com.sw.ck.common.datascope.DataScopeType;
import com.sw.ck.workflow.api.dto.ApproverContext;
import com.sw.ck.workflow.api.spi.ApproverResolver;
import com.sw.ck.workflow.entity.WorkflowFormBinding;
import com.sw.ck.workflow.mapper.WorkflowFormBindingMapper;
import com.sw.ck.workflow.resolver.FixedApproverResolver;
import com.sw.ck.workflow.service.WorkflowFormBindingService;
import com.sw.ck.workflow.service.impl.WorkflowFormBindingServiceImpl;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.spring.ProcessEngineFactoryBean;
import org.flowable.spring.SpringProcessEngineConfiguration;
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
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M04 第三环第 3 步：验证 BPMN 部署 + 审批人解析 SPI + 表单绑定。
 * <p>
 * 验证范围：
 * <ul>
 *   <li>BPMN 部署带 tenantId，可通过 process key + tenantId 查询到流程定义</li>
 *   <li>BPMN userTask assignee 是表达式 {@code ${approver}}，非写死用户</li>
 *   <li>{@link FixedApproverResolver} 可注入且返回非空审批人</li>
 *   <li>表单绑定 {@code it_application → skeleton_approval} 可写入并正确携带 tenantId</li>
 * </ul>
 * <p>
 * 注意：本测试不自动触发 {@link WorkflowDeployRunner}（Runner 在 {@code @SpringBootTest}
 * 自定义 TestConfig 中未注册），部署/绑定操作由测试方法显式完成，以隔离验证步骤。
 * </p>
 */
@SpringBootTest(
        classes = WorkflowDeployIntegrationTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.flyway.enabled=false",
                "sw.workflow.enabled=true",
                "sw.form.enabled=true",
                "spring.flowable.check-process-definitions=false"
        }
)
@DisplayName("Workflow BPMN 部署 + SPI + 绑定验证")
class WorkflowDeployIntegrationTest {

    private static final Long TENANT_0 = 0L;
    private static final Long USER_0 = 0L;
    private static final String PROCESS_KEY = "skeleton_approval";

    /** 在 {@code @BeforeAll} 中部署后记下 ID，供后续测试查询 BpmnModel */
    private static String processDefId;

    // ==================== 注入 ====================

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WorkflowFormBindingService bindingService;

    @Autowired
    private FixedApproverResolver fixedApproverResolver;

    @Autowired
    private TestLoginContext testLoginContext;

    // ==================== 表创建 + BPMN 部署 ====================

    @BeforeAll
    static void beforeAll(@Autowired RepositoryService rs,
                          @Autowired JdbcTemplate jt) {
        // 1. 创建 workflow 元数据表
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

        // 2. 部署骨架审批流程（带 tenantId）
        Deployment deployment = rs.createDeployment()
                .addClasspathResource("processes/" + PROCESS_KEY + ".bpmn20.xml")
                .tenantId(String.valueOf(TENANT_0))
                .name("Skeleton Approval")
                .deploy();

        ProcessDefinition def = rs.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .singleResult();
        processDefId = def.getId();
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM sw_workflow_form_binding");
        testLoginContext.set(TENANT_0, USER_0);
    }

    // ==================== 测试 1：BPMN 部署 + tenantId ====================

    @Test
    @DisplayName("BPMN 部署带 tenantId → 按 key + tenantId 可查到流程定义")
    void deployFlowableBpmn_shouldCreateDeploymentWithTenantId() {
        // @BeforeAll 中已部署；此处验证查询

        // 按 process key + tenantId 查询
        ProcessDefinition def = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(PROCESS_KEY)
                .processDefinitionTenantId(String.valueOf(TENANT_0))
                .latestVersion()
                .singleResult();

        assertThat(def)
                .as("应有流程定义 key=%s, tenantId=%s", PROCESS_KEY, TENANT_0)
                .isNotNull();
        assertThat(def.getTenantId())
                .as("tenantId 应与部署时传入一致")
                .isEqualTo(String.valueOf(TENANT_0));

        // 输出
        System.out.println("=== BPMN 部署验证 ===");
        System.out.println("  processKey=" + PROCESS_KEY + ", tenantId=" + def.getTenantId()
                + ", deploymentId=" + def.getDeploymentId() + " ✓");
    }

    // ==================== 测试 2：BPMN userTask assignee 表达式 ====================

    @Test
    @DisplayName("BPMN userTask assignee 为 ${approver} 表达式，非写死用户")
    void bpmnUserTask_shouldUseExpressionAssignee() {
        // 读 BpmnModel
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefId);
        assertThat(bpmnModel).as("应能通过 processDefId 加载 BpmnModel").isNotNull();

        org.flowable.bpmn.model.Process mainProcess = bpmnModel.getMainProcess();
        assertThat(mainProcess).as("BPMN 应有主流程").isNotNull();

        List<UserTask> userTasks = mainProcess.findFlowElementsOfType(UserTask.class);
        assertThat(userTasks)
                .as("应至少有一个 userTask")
                .isNotEmpty();

        // 检查审批节点的 assignee
        UserTask approvalTask = userTasks.get(0);
        String assignee = approvalTask.getAssignee();

        assertThat(assignee)
                .as("userTask assignee 应为表达式 ${approver}")
                .isEqualTo("${approver}");

        System.out.println("=== BPMN userTask 验证 ===");
        System.out.println("  userTaskId=" + approvalTask.getId()
                + ", name=" + approvalTask.getName()
                + ", assignee=" + assignee + " ✓");
    }

    // ==================== 测试 3：FixedApproverResolver ====================

    @Test
    @DisplayName("FixedApproverResolver 可注入，resolve(上下文) 返回提交者")
    void fixedApproverResolver_shouldReturnSubmitter() {
        ApproverContext ctx = new ApproverContext();
        ctx.setFormKey("it_application");
        ctx.setSubmitter(1L);
        ctx.setTenantId(TENANT_0);

        String approver = fixedApproverResolver.resolve(ctx);

        assertThat(approver)
                .as("审批人 ID 不应为空")
                .isNotNull();
        assertThat(approver)
                .as("骨架阶段返回 submitter")
                .isEqualTo("1");

        System.out.println("=== Resolver 验证 ===");
        System.out.println("  resolver=" + fixedApproverResolver.getClass().getSimpleName()
                + ", approver=" + approver + " ✓");
    }

    // ==================== 测试 4：表单绑定（含租户隔离） ====================

    @Test
    @DisplayName("插入 it_application → skeleton_approval 绑定 → 查回确认 tenantId 正确")
    void formBinding_shouldBeInsertableWithTenantId() {
        // Arrange：只填业务列
        WorkflowFormBinding binding = new WorkflowFormBinding();
        binding.setFormKey("it_application");
        binding.setProcessDefKey(PROCESS_KEY);
        binding.setActive(true);

        // Act
        bindingService.save(binding);

        // Assert 1：findActiveByFormKey 查回
        List<WorkflowFormBinding> found = bindingService.findActiveByFormKey("it_application");
        assertThat(found)
                .as("应查到 1 条启用绑定")
                .hasSize(1);

        WorkflowFormBinding row = found.get(0);
        assertThat(row.getProcessDefKey()).isEqualTo(PROCESS_KEY);
        assertThat(row.getFormKey()).isEqualTo("it_application");
        assertThat(row.getActive()).isTrue();

        // Assert 2：拦截器自动注入基列
        assertThat(row.getTenantId())
                .as("tenantId 应被自动注入为 0")
                .isEqualTo(TENANT_0);
        assertThat(row.getCreateBy())
                .as("createBy 应被自动注入")
                .isEqualTo(USER_0);
        assertThat(row.getVersion())
                .as("version 应被自动注入为 0")
                .isZero();

        System.out.println("=== 绑定行验证 ===");
        System.out.println("  formKey=it_application, processDefKey=" + PROCESS_KEY)
        ;
        System.out.println("  tenantId=" + row.getTenantId() + ", createBy=" + row.getCreateBy()
                + ", version=" + row.getVersion() + " ✓");
    }

    // ==================== 测试基础结构 ====================

    /**
     * 可编程的 LoginContextProvider，用于测试中切换当前用户/租户，
     * 使 MyBatis-Plus 拦截器在插入绑定时真实生效。
     */
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
    @MapperScan("com.sw.ck.workflow.mapper")
    static class TestConfig {

        // ---- DataSource ----

        @Bean
        public DataSource dataSource() {
            return DataSourceBuilder.create()
                    // Flowable ACT_* 表的 DDL 使用 H2 方言（IDENTITY 类型），
                    // 与 PostgreSQL 模式冲突，因此优先使用 H2 默认模式。
                    .url("jdbc:h2:mem:wfdeploytest;DB_CLOSE_DELAY=-1")
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
            return new TenantProperties();
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
            factory.setTypeAliasesPackage("com.sw.ck.workflow.entity");
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

        // ---- 业务 Service ----

        @Bean
        public WorkflowFormBindingService workflowFormBindingService(
                WorkflowFormBindingMapper mapper) {
            return new WorkflowFormBindingServiceImpl();
        }

        // ---- 审批人解析器 ----

        @Bean
        public FixedApproverResolver fixedApproverResolver() {
            return new FixedApproverResolver();
        }
    }
}
