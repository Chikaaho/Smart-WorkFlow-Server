package com.sw.ck.agent.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.sw.ck.agent.dto.AgentOrchestrationRunReqDTO;
import com.sw.ck.agent.dto.AgentOrchestrationRunRespDTO;
import com.sw.ck.agent.entity.AgentModelConfig;
import com.sw.ck.agent.mapper.AgentModelConfigMapper;
import com.sw.ck.agent.orchestration.AgentGraphFactory;
import com.sw.ck.agent.orchestration.ChatModelFactory;
import com.sw.ck.agent.service.AgentOrchestrationService;
import com.sw.ck.common.config.mybatis.CommonMetaObjectHandler;
import com.sw.ck.common.config.mybatis.tenant.CommonTenantLineHandler;
import com.sw.ck.common.config.mybatis.tenant.TenantProperties;
import com.sw.ck.common.crypto.AesGcmCipher;
import com.sw.ck.common.datascope.DataScopeType;
import com.sw.ck.common.exception.BaseException;
import com.sw.ck.common.exception.CommonErrorCode;
import com.sw.ck.common.security.LoginContextProvider;
import com.sw.ck.security.holder.LoginUser;
import com.sw.ck.security.holder.LoginUserHolder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.state.AgentState;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AgentOrchestrationServiceImpl} 测试（M07 Step2 §13.2）。
 * <p>
 * 策略与 Step1 的 {@code AgentModelConfigServiceImplTest} 同款：{@code @SpringBootTest}
 * + H2（TestConfig 组合装配）+ {@code @Transactional} 回滚；端到端用 JDK 内置
 * {@code com.sun.net.httpserver.HttpServer}（localhost:0 随机端口）mock OpenAI Chat
 * Completions 服务（监听 baseUrl + {@code /v1/chat/completions}，Spring AI 默认
 * completionsPath，实测确认）。
 * </p>
 * <p>
 * 用例 3 安全断言：模型服务不可达时 {@code errorMessage} 非空且不含明文 API Key
 * （密文经真实 AesGcmCipher 解密后进入客户端构造，异常信息不得泄漏）。
 * </p>
 */
@SpringBootTest(
        classes = AgentOrchestrationServiceImplTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.autoconfigure.exclude=com.sw.ck.common.config.mybatis.MybatisPlusConfig,"
                        + "com.sw.ck.common.config.redis.RedisConfig,"
                        + "com.sw.ck.security.config.SecurityAutoConfiguration,"
                        + "com.sw.ck.security.config.WebSecurityAutoConfiguration"
        }
)
@Transactional
@DisplayName("编排执行引擎 Service 测试")
class AgentOrchestrationServiceImplTest {

    private static final Long TENANT_100 = 100L;
    private static final Long USER_1 = 1L;
    private static final String TEST_API_KEY = "sk-test-123456";

    @Autowired
    private AgentOrchestrationService service;

    @Autowired
    private AgentModelConfigMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AesGcmCipher cipher;

    // ==================== 建表（V19 H2 脚本 DDL） ====================

    @BeforeAll
    static void createTables(@Autowired JdbcTemplate jt) {
        jt.execute("""
                CREATE TABLE IF NOT EXISTS sw_agent_model_config (
                    id              BIGINT NOT NULL PRIMARY KEY,
                    name            VARCHAR(100) NOT NULL,
                    protocol_type   VARCHAR(32) NOT NULL,
                    base_url        VARCHAR(500) NOT NULL,
                    model_name      VARCHAR(100) NOT NULL,
                    api_key_cipher  CLOB,
                    temperature     DECIMAL(4,2),
                    max_tokens      INT,
                    top_p           DECIMAL(4,2),
                    timeout_seconds INT NOT NULL DEFAULT 30,
                    retry_count     INT NOT NULL DEFAULT 0,
                    enabled         SMALLINT NOT NULL DEFAULT 1,
                    remark          VARCHAR(500),
                    create_time     TIMESTAMP,
                    create_by       VARCHAR(64),
                    update_time     TIMESTAMP,
                    update_by       VARCHAR(64),
                    deleted         SMALLINT NOT NULL DEFAULT 0,
                    tenant_id       BIGINT NOT NULL DEFAULT 0,
                    version         BIGINT NOT NULL DEFAULT 0
                )
                """);
        jt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_sw_agent_model_name ON sw_agent_model_config (tenant_id, name)");
        jt.execute("CREATE INDEX IF NOT EXISTS idx_sw_agent_model_tenant_deleted ON sw_agent_model_config (tenant_id, deleted)");
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM sw_agent_model_config");
        setLoginUser(TENANT_100, USER_1);
    }

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    private void setLoginUser(Long tenantId, Long userId) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(userId);
        loginUser.setTenantId(tenantId);
        loginUser.setUsername("user_" + userId);
        LoginUserHolder.set(loginUser);
    }

    // ==================== 用例 1：配置不存在 ====================

    @Test
    @DisplayName("用例1: agentModelConfigId 不存在 → 抛 NOT_FOUND 业务异常（404 语义，不进入图执行）")
    void run_unknownId_shouldThrowNotFound() {
        AgentOrchestrationRunReqDTO req = new AgentOrchestrationRunReqDTO();
        req.setAgentModelConfigId(999999L);
        req.setInput("hello");

        assertThatThrownBy(() -> service.run(req))
                .isInstanceOf(BaseException.class)
                .satisfies(ex -> assertThat(((BaseException) ex).getCode())
                        .isEqualTo(CommonErrorCode.NOT_FOUND.getCode()));
    }

    // ==================== 用例 2：端到端成功路径 ====================

    @Test
    @DisplayName("用例2: openai 配置 + 本地 mock Chat Completions 服务 → success=true，output 与 mock 回复一致")
    void run_openaiWithMockServer_shouldSucceed() throws Exception {
        HttpServer server = startChatServer();
        try {
            int port = server.getAddress().getPort();
            Long id = insertConfig("openai", "http://127.0.0.1:" + port, TEST_API_KEY);

            AgentOrchestrationRunRespDTO resp = service.run(req(id, "你好"));

            assertThat(resp.isSuccess()).isTrue();
            assertThat(resp.getOutput()).isEqualTo("你好，mock 回复");
            assertThat(resp.getErrorMessage()).isNull();
            assertThat(resp.getLatencyMs()).isGreaterThanOrEqualTo(0);
        } finally {
            server.stop(0);
        }
    }

    // ==================== 用例 3：模型服务不可达 ====================

    @Test
    @DisplayName("用例3: mock 服务器未监听 → success=false，errorMessage 非空且不含明文 API Key")
    void run_unreachableServer_shouldReturnFailure() throws Exception {
        int unusedPort = findUnusedPort();
        Long id = insertConfig("openai", "http://127.0.0.1:" + unusedPort, TEST_API_KEY);

        AgentOrchestrationRunRespDTO resp = service.run(req(id, "hello"));

        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.getErrorMessage()).isNotBlank();
        // 安全断言：异常摘要不得泄漏明文 Key（方案 §12 风险表）
        assertThat(resp.getErrorMessage())
                .as("errorMessage 不得包含明文 API Key")
                .doesNotContain(TEST_API_KEY)
                .doesNotContain("sk-");
        assertThat(resp.getLatencyMs()).isGreaterThanOrEqualTo(0);
    }

    // ==================== 测试数据工厂 ====================

    private AgentOrchestrationRunReqDTO req(Long id, String input) {
        AgentOrchestrationRunReqDTO req = new AgentOrchestrationRunReqDTO();
        req.setAgentModelConfigId(id);
        req.setInput(input);
        return req;
    }

    private Long insertConfig(String protocol, String baseUrl, String apiKey) {
        AgentModelConfig entity = new AgentModelConfig();
        entity.setName("orch-test-" + System.nanoTime());
        entity.setProtocolType(protocol);
        entity.setBaseUrl(baseUrl);
        entity.setModelName("gpt-4o");
        entity.setApiKeyCipher(apiKey == null ? null : cipher.encrypt(apiKey));
        entity.setTimeoutSeconds(10);
        entity.setRetryCount(0);
        entity.setEnabled(true);
        mapper.insert(entity);
        return entity.getId();
    }

    // ==================== 本地假 OpenAI Chat Completions 服务 ====================

    private HttpServer startChatServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            // 响应结构为 OpenAI Chat Completions 合法 JSON（Spring AI 1.0.4 实测可解析）
            String json = "{\"id\":\"chatcmpl-test-1\",\"object\":\"chat.completion\",\"created\":1720000000,"
                    + "\"model\":\"gpt-4o\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\","
                    + "\"content\":\"你好，mock 回复\"},\"finish_reason\":\"stop\"}],"
                    + "\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":5,\"total_tokens\":8}}";
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static int findUnusedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }
    }

    // ==================== 组合测试配置 ====================

    /** 测试加密密钥：32 字节 "0123456789abcdef0123456789abcdef" 的 Base64（Step1 同款） */
    static final String TEST_BASE64_KEY =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @Configuration
    @MapperScan("com.sw.ck.agent.mapper")
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        public DataSource dataSource() {
            return DataSourceBuilder.create()
                    .url("jdbc:h2:mem:agentorch;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
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
        public LoginContextProvider testLoginContextProvider() {
            return new LoginContextProvider() {
                @Override
                public Long getUserId() {
                    LoginUser user = LoginUserHolder.get();
                    return user != null ? user.getUserId() : null;
                }

                @Override
                public Long getTenantId() {
                    LoginUser user = LoginUserHolder.get();
                    return user != null ? user.getTenantId() : null;
                }

                @Override
                public Long getDeptId() {
                    LoginUser user = LoginUserHolder.get();
                    return user != null ? user.getDeptId() : null;
                }

                @Override
                public DataScopeType getDataScopeType() {
                    return DataScopeType.ALL;
                }

                @Override
                public Set<Long> getCustomDeptIds() {
                    return Set.of();
                }

                @Override
                public boolean isSuperAdmin() {
                    LoginUser user = LoginUserHolder.get();
                    return user != null && user.isSuperAdmin();
                }
            };
        }

        @Bean
        public CommonMetaObjectHandler commonMetaObjectHandler(LoginContextProvider loginContextProvider) {
            return new CommonMetaObjectHandler(loginContextProvider);
        }

        @Bean
        public TenantProperties tenantProperties() {
            TenantProperties props = new TenantProperties();
            props.setEnabled(true);
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
        public MybatisPlusInterceptor mybatisPlusInterceptor(TenantLineInnerInterceptor tenantLineInnerInterceptor) {
            MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(tenantLineInnerInterceptor);
            interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
            interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
            return interceptor;
        }

        @Bean
        public org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory(
                DataSource dataSource,
                CommonMetaObjectHandler metaObjectHandler,
                MybatisPlusInterceptor interceptor) throws Exception {
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setTypeAliasesPackage("com.sw.ck.agent.entity");
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
        public AesGcmCipher aesGcmCipher() {
            return new AesGcmCipher(TEST_BASE64_KEY);
        }

        // ==================== M07 Step2 业务 Bean ====================

        @Bean
        public ChatModelFactory chatModelFactory() {
            return new ChatModelFactory();
        }

        @Bean
        public CompiledGraph<AgentState> agentCompiledGraph() throws GraphStateException {
            return new AgentGraphFactory().buildGraph();
        }

        @Bean
        public AgentOrchestrationService agentOrchestrationService(
                AgentModelConfigMapper agentModelConfigMapper,
                AesGcmCipher aesGcmCipher,
                ChatModelFactory chatModelFactory,
                CompiledGraph<AgentState> agentCompiledGraph) {
            return new AgentOrchestrationServiceImpl(
                    agentModelConfigMapper, aesGcmCipher, chatModelFactory, agentCompiledGraph);
        }
    }
}
