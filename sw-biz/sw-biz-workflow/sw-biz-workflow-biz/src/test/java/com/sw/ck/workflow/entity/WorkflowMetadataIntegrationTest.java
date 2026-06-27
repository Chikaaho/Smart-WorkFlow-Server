package com.sw.ck.workflow.entity;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.sw.ck.common.config.mybatis.CommonMetaObjectHandler;
import com.sw.ck.common.config.mybatis.tenant.CommonTenantLineHandler;
import com.sw.ck.common.config.mybatis.tenant.TenantProperties;
import com.sw.ck.common.constant.CommonConstants;
import com.sw.ck.common.security.LoginContextProvider;
import com.sw.ck.workflow.mapper.WorkflowFormBindingMapper;
import com.sw.ck.workflow.mapper.WorkflowInstanceMapper;
import com.sw.ck.workflow.service.WorkflowFormBindingService;
import com.sw.ck.workflow.service.WorkflowInstanceService;
import com.sw.ck.workflow.service.impl.WorkflowFormBindingServiceImpl;
import com.sw.ck.workflow.service.impl.WorkflowInstanceServiceImpl;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M04 第三环第 2 步：验证 sw_workflow_* 元数据表 + 实体/Mapper/Service 的基础功能。
 * <p>
 * 验证范围：
 * <ul>
 *   <li>BaseEntity 继承 + 拦截器自动注入 tenant_id/审计列/deleted/version</li>
 *   <li>表单↔流程绑定：只填业务列 → findActiveByFormKey 查回 → 检查基列自动注入</li>
 *   <li>流程实例：插入 status=RUNNING → 断言落库正确</li>
 *   <li>租户隔离：同 form_key 不同租户 → 查询只返回当前租户那条</li>
 * </ul>
 * </p>
 */
@SpringBootTest(
        classes = WorkflowMetadataIntegrationTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.flyway.enabled=false",
                "sw.tenant.enabled=true"
        }
)
@DisplayName("Workflow 元数据表 + 数据访问层验证")
class WorkflowMetadataIntegrationTest {

    private static final Long USER_A = 1L;
    private static final Long USER_B = 2L;
    private static final Long TENANT_100 = 100L;
    private static final Long TENANT_200 = 200L;
    private static final String FORM_KEY_A = "expense_report";
    private static final String FORM_KEY_B = "leave_request";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WorkflowFormBindingService bindingService;

    @Autowired
    private WorkflowInstanceService instanceService;

    @Autowired
    private TestLoginContext testLoginContext;

    // ==================== 表创建 ====================

    @BeforeAll
    static void createTables(@Autowired JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
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
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sw_workflow_instance (
                    id                   bigint          not null primary key,
                    create_time          timestamp       not null default current_timestamp,
                    create_by            bigint,
                    update_time          timestamp       not null default current_timestamp,
                    update_by            bigint,
                    deleted              smallint        not null default 0,
                    tenant_id            bigint          not null default 0,
                    version              bigint          not null default 0,
                    process_instance_id  varchar(64)     not null,
                    process_def_key      varchar(200)    not null,
                    business_key         varchar(36)     not null,
                    form_key             varchar(200)    not null,
                    initiator_id         bigint          not null,
                    status               varchar(20)     not null default 'RUNNING'
                )
                """);
        // 索引无需手动创建（Flyway 脚本中已有），测试验证不依赖索引
    }

    @BeforeEach
    void setUp() {
        cleanUp();
        testLoginContext.set(TENANT_100, USER_A);
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM sw_workflow_form_binding");
        jdbcTemplate.update("DELETE FROM sw_workflow_instance");
    }

    // ==================== 测试 1：自动注入地基契约 ====================

    @Test
    @DisplayName("插 binding(只填业务列) → 查回确认基列被拦截器自动注入")
    void bindingAutoFill_shouldInjectBaseColumns() {
        // —— Arrange：只填业务列，不填基列 ——
        WorkflowFormBinding binding = new WorkflowFormBinding();
        binding.setFormKey(FORM_KEY_A);
        binding.setProcessDefKey("process_expense");
        binding.setActive(true);

        // —— Act ——
        bindingService.save(binding);
        Long savedId = binding.getId();

        // —— Assert：查回 ——
        WorkflowFormBinding found = bindingService.getById(savedId);
        assertThat(found).as("应能通过 id 查回绑定").isNotNull();

        // 基列自动注入验证
        assertThat(found.getTenantId()).as("tenant_id 应被自动注入").isEqualTo(TENANT_100);
        assertThat(found.getCreateTime()).as("createTime 应被自动注入").isNotNull();
        assertThat(found.getUpdateTime()).as("updateTime 应被自动注入").isNotNull();
        assertThat(found.getCreateBy()).as("createBy 应被自动注入").isEqualTo(USER_A);
        assertThat(found.getUpdateBy()).as("updateBy 应被自动注入").isEqualTo(USER_A);
        assertThat(found.getDeleted()).as("deleted 应被自动注入为 0").isZero();
        assertThat(found.getVersion()).as("version 应被自动注入为 0").isZero();

        // 业务列正确
        assertThat(found.getFormKey()).isEqualTo(FORM_KEY_A);
        assertThat(found.getProcessDefKey()).isEqualTo("process_expense");
        assertThat(found.getActive()).isTrue();

        // 输出落库行内容
        System.out.println("=== binding 自动注入验证 ===");
        System.out.println("  id=" + found.getId() + ", tenantId=" + found.getTenantId()
                + ", createBy=" + found.getCreateBy() + ", createTime=" + found.getCreateTime()
                + ", deleted=" + found.getDeleted() + ", version=" + found.getVersion()
                + ", formKey=" + found.getFormKey() + ", active=" + found.getActive() + " ✓");
    }

    // ==================== 测试 2：流程实例落库 ====================

    @Test
    @DisplayName("插 instance(status=RUNNING) → 断言落库正确")
    void instanceInsert_shouldStoreCorrectly() {
        // —— Arrange ——
        WorkflowInstance instance = new WorkflowInstance();
        instance.setProcessInstanceId("flowable_inst_001");
        instance.setProcessDefKey("process_leave");
        instance.setBusinessKey("rec_abc123");
        instance.setFormKey(FORM_KEY_B);
        instance.setInitiatorId(USER_A);
        instance.setStatus(InstanceStatusEnum.RUNNING.getCode());

        // —— Act ——
        instanceService.save(instance);
        Long savedId = instance.getId();

        // —— Assert ——
        WorkflowInstance found = instanceService.getById(savedId);
        assertThat(found).isNotNull();
        assertThat(found.getTenantId()).isEqualTo(TENANT_100);
        assertThat(found.getStatus()).isEqualTo(InstanceStatusEnum.RUNNING.getCode());
        assertThat(found.getProcessInstanceId()).isEqualTo("flowable_inst_001");
        assertThat(found.getBusinessKey()).isEqualTo("rec_abc123");
        assertThat(found.getInitiatorId()).isEqualTo(USER_A);
        assertThat(found.getVersion()).isZero();

        System.out.println("=== instance 落库验证 ===");
        System.out.println("  id=" + found.getId() + ", tenantId=" + found.getTenantId()
                + ", status=" + found.getStatus() + ", processInstanceId=" + found.getProcessInstanceId()
                + ", businessKey=" + found.getBusinessKey() + ", version=" + found.getVersion() + " ✓");
    }

    // ==================== 测试 3：租户隔离 ====================

    @Test
    @DisplayName("不同租户插同 form_key 绑定 → 查询只返回当前租户那条")
    void tenantIsolation_shouldSeparateBindings() {
        // —— Arrange：TENANT_100 插一条 ——
        testLoginContext.set(TENANT_100, USER_A);
        WorkflowFormBinding binding100 = new WorkflowFormBinding();
        binding100.setFormKey(FORM_KEY_A);
        binding100.setProcessDefKey("proc_100");
        binding100.setActive(true);
        bindingService.save(binding100);

        // TENANT_200 插一条（同 form_key）
        testLoginContext.set(TENANT_200, USER_B);
        WorkflowFormBinding binding200 = new WorkflowFormBinding();
        binding200.setFormKey(FORM_KEY_A);
        binding200.setProcessDefKey("proc_200");
        binding200.setActive(true);
        bindingService.save(binding200);

        // —— Act & Assert：TENANT_100 上下文查询 ——
        testLoginContext.set(TENANT_100, USER_A);
        List<WorkflowFormBinding> results100 = bindingService.findActiveByFormKey(FORM_KEY_A);
        assertThat(results100)
                .as("TENANT_100 应查到自己的 1 条绑定")
                .hasSize(1);
        assertThat(results100.get(0).getProcessDefKey())
                .as("应返回 TENANT_100 的流程定义")
                .isEqualTo("proc_100");
        assertThat(results100.get(0).getTenantId())
                .as("tenant_id 应为 100")
                .isEqualTo(TENANT_100);

        // —— Act & Assert：TENANT_200 上下文查询 ——
        testLoginContext.set(TENANT_200, USER_B);
        List<WorkflowFormBinding> results200 = bindingService.findActiveByFormKey(FORM_KEY_A);
        assertThat(results200)
                .as("TENANT_200 应查到自己的 1 条绑定")
                .hasSize(1);
        assertThat(results200.get(0).getProcessDefKey())
                .as("应返回 TENANT_200 的流程定义")
                .isEqualTo("proc_200");
        assertThat(results200.get(0).getTenantId())
                .as("tenant_id 应为 200")
                .isEqualTo(TENANT_200);

        System.out.println("=== 租户隔离验证 ===");
        System.out.println("  TENANT_100: formKey=" + FORM_KEY_A + " → proc_100 ✓");
        System.out.println("  TENANT_200: formKey=" + FORM_KEY_A + " → proc_200 ✓");
    }

    // ==================== 测试上下文配置 ====================

    /**
     * 可编程的 LoginContextProvider，通过 {@link #set(Long, Long)} 切换当前用户/租户，
     * 让 MyBatis-Plus 拦截器（自动填充 + 租户行级隔离）在测试中真实生效。
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
        public com.sw.ck.common.datascope.DataScopeType getDataScopeType() {
            return com.sw.ck.common.datascope.DataScopeType.ALL;
        }

        @Override
        public java.util.Set<Long> getCustomDeptIds() { return java.util.Set.of(); }

        @Override
        public boolean isSuperAdmin() { return false; }
    }

    @Configuration
    @MapperScan("com.sw.ck.workflow.mapper")
    static class TestConfig {

        @Bean
        public DataSource dataSource() {
            return DataSourceBuilder.create()
                    .url("jdbc:h2:mem:workflowmetadata;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
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
        public TestLoginContext testLoginContext() {
            return new TestLoginContext();
        }

        @Bean
        public CommonMetaObjectHandler commonMetaObjectHandler(LoginContextProvider loginContextProvider) {
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

        @Bean
        public WorkflowFormBindingService workflowFormBindingService(
                WorkflowFormBindingMapper mapper) {
            return new WorkflowFormBindingServiceImpl();
        }

        @Bean
        public WorkflowInstanceService workflowInstanceService(
                WorkflowInstanceMapper mapper) {
            return new WorkflowInstanceServiceImpl();
        }
    }
}
