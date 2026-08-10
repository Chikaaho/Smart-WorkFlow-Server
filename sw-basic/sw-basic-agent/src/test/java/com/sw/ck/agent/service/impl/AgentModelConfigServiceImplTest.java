package com.sw.ck.agent.service.impl;

import com.sw.ck.agent.dto.AgentModelConfigDTO;
import com.sw.ck.agent.dto.AgentModelSaveReqDTO;
import com.sw.ck.agent.dto.AgentModelTestConnectionRespDTO;
import com.sw.ck.agent.entity.AgentModelConfig;
import com.sw.ck.agent.mapper.AgentModelConfigMapper;
import com.sw.ck.agent.service.AgentModelConfigService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.sw.ck.common.config.mybatis.CommonMetaObjectHandler;
import com.sw.ck.common.config.mybatis.tenant.CommonTenantLineHandler;
import com.sw.ck.common.config.mybatis.tenant.TenantProperties;
import com.sw.ck.common.crypto.AesGcmCipher;
import com.sw.ck.common.datascope.DataScopeType;
import com.sw.ck.common.exception.BaseException;
import com.sw.ck.common.exception.CommonErrorCode;
import com.sw.ck.common.page.PageParam;
import com.sw.ck.common.page.PageResult;
import com.sw.ck.common.security.LoginContextProvider;
import com.sw.ck.security.holder.LoginUser;
import com.sw.ck.security.holder.LoginUserHolder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
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
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AgentModelConfigServiceImpl} 测试（M07 Step1 §13.2 表格 11 用例）。
 * <p>
 * 策略：{@code @SpringBootTest} + H2（TestConfig 组合装配，参照 NotifyControllerIntegrationTest
 * 先例）+ {@code @Transactional}（每用例回滚）。连通性测试用 JDK 内置
 * {@code com.sun.net.httpserver.HttpServer}（localhost:0 随机端口），不依赖真实外网、
 * 不引入 WireMock 等新测试依赖。
 * </p>
 * <p>
 * 测试密钥（fake key）同样走加密流程：用例 1 断言落库为密文且可被 decrypt 还原，
 * 绝不断言明文落库（方案 §8 硬约束）。
 * </p>
 */
@SpringBootTest(
        classes = AgentModelConfigServiceImplTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                // 排除 sw-common / sw-security 自动配置，避免与 TestConfig 手动装配的
                // MetaObjectHandler/LoginContextProvider 等 bean 定义冲突（先例 TestConfig 模式）
                "spring.autoconfigure.exclude=com.sw.ck.common.config.mybatis.MybatisPlusConfig,"
                        + "com.sw.ck.common.config.redis.RedisConfig,"
                        + "com.sw.ck.security.config.SecurityAutoConfiguration,"
                        + "com.sw.ck.security.config.WebSecurityAutoConfiguration"
        }
)
@Transactional
@DisplayName("大模型接入配置 Service 测试")
class AgentModelConfigServiceImplTest {

    private static final Long TENANT_100 = 100L;
    private static final Long TENANT_200 = 200L;
    private static final Long USER_1 = 1L;
    private static final String TEST_API_KEY = "sk-test-123456";

    @Autowired
    private AgentModelConfigService service;

    @Autowired
    private AgentModelConfigMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
                    version         BIGINT NOT NULL DEFAULT 0,
                    group_key       VARCHAR(100),
                    sort            INT NOT NULL DEFAULT 0,
                    locked_until    TIMESTAMP,
                    quota_cooldown_seconds INT NOT NULL DEFAULT 60
                )
                """);
        jt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_sw_agent_model_name ON sw_agent_model_config (tenant_id, name)");
        jt.execute("CREATE INDEX IF NOT EXISTS idx_sw_agent_model_tenant_deleted ON sw_agent_model_config (tenant_id, deleted)");
        jt.execute("CREATE INDEX IF NOT EXISTS idx_sw_agent_model_group ON sw_agent_model_config (tenant_id, group_key, sort)");
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

    // ==================== 测试数据工厂 ====================

    private AgentModelSaveReqDTO createReq(String name, String protocolType, String apiKey) {
        AgentModelSaveReqDTO req = new AgentModelSaveReqDTO();
        req.setName(name);
        req.setProtocolType(protocolType);
        req.setBaseUrl("http://localhost:1");
        req.setModelName("gpt-4o");
        req.setApiKey(apiKey);
        req.setTimeoutSeconds(30);
        req.setRetryCount(0);
        req.setEnabled(true);
        return req;
    }

    // ==================== 用例 1/2：加密落库 ====================

    @Test
    @DisplayName("用例1: create 后 apiKeyCipher 非明文落库，且可被 decrypt 还原")
    void create_shouldEncryptApiKey() {
        Long id = service.create(createReq("model-1", "openai", TEST_API_KEY));

        AgentModelConfig saved = mapper.selectById(id);
        assertThat(saved.getApiKeyCipher())
                .as("落库密文不得等于明文")
                .isNotEqualTo(TEST_API_KEY)
                .isNotNull();
        // 密文可还原（测试密钥走真实加密流程，不断言明文落库）
        assertThat(new AesGcmCipher(TEST_BASE64_KEY).decrypt(saved.getApiKeyCipher()))
                .isEqualTo(TEST_API_KEY);
    }

    @Test
    @DisplayName("用例2: create 时 apiKey 为空 → apiKeyCipher 落库为 null")
    void create_withoutApiKey_shouldStoreNullCipher() {
        Long id = service.create(createReq("ollama-local", "ollama", null));

        AgentModelConfig saved = mapper.selectById(id);
        assertThat(saved.getApiKeyCipher()).isNull();
    }

    // ==================== 用例 3：update 空 Key 不覆盖 ====================

    @Test
    @DisplayName("用例3: update 时 apiKey 为空不覆盖旧密文")
    void update_withEmptyApiKey_shouldKeepOldCipher() {
        Long id = service.create(createReq("model-3", "openai", "old-key-abc"));

        AgentModelSaveReqDTO updateReq = createReq("model-3", "openai", "");
        service.update(id, updateReq);

        AgentModelConfig saved = mapper.selectById(id);
        assertThat(new AesGcmCipher(TEST_BASE64_KEY).decrypt(saved.getApiKeyCipher()))
                .as("空 Key 更新后旧密文应原样保留")
                .isEqualTo("old-key-abc");
    }

    // ==================== 用例 4：脱敏格式 ====================

    @Test
    @DisplayName("用例4: getById 返回 DTO 的 apiKeyMasked 格式正确（前2后2+****），DTO 不含 apiKeyCipher 字段")
    void getById_shouldReturnMaskedKey() {
        Long id = service.create(createReq("model-4", "openai", TEST_API_KEY));

        AgentModelConfigDTO dto = service.getById(id);
        assertThat(dto.getApiKeyMasked())
                .as("脱敏格式应为前2后2 + ****")
                .isEqualTo("sk****56");
        // DTO 类不得含 apiKeyCipher 字段（编译期与序列化双重防线）
        boolean hasCipherField = Arrays.stream(AgentModelConfigDTO.class.getDeclaredFields())
                .anyMatch(f -> f.getName().equals("apiKeyCipher"));
        assertThat(hasCipherField).as("AgentModelConfigDTO 不应声明 apiKeyCipher 字段").isFalse();
    }

    // ==================== 用例 5：name 唯一性 ====================

    @Test
    @DisplayName("用例5: 同租户重复 name 抛业务异常；跨租户同名允许")
    void create_duplicateName_sameTenantThrows_crossTenantAllowed() {
        service.create(createReq("gpt-shared", "openai", TEST_API_KEY));

        // 同租户重复 → 业务异常，不落库
        assertThatThrownBy(() -> service.create(createReq("gpt-shared", "openai", "other-key")))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("已存在");

        // 跨租户同名 → 允许
        setLoginUser(TENANT_200, USER_1);
        Long crossTenantId = service.create(createReq("gpt-shared", "openai", TEST_API_KEY));
        assertThat(crossTenantId).isNotNull();
        assertThat(mapper.selectById(crossTenantId).getTenantId()).isEqualTo(TENANT_200);
    }

    // ==================== 用例 6：protocolType 白名单 ====================

    @Test
    @DisplayName("用例6: protocolType 非法值拒绝，不落库")
    void create_withInvalidProtocol_shouldThrow() {
        AgentModelSaveReqDTO req = createReq("bad-proto", "foo", TEST_API_KEY);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("不支持的协议类型");
        assertThat(mapper.selectCount(null))
                .as("非法协议不应落库")
                .isZero();
    }

    // ==================== 用例 7：分页 + 关键字过滤 ====================

    @Test
    @DisplayName("用例7: pageModels 分页 + nameKeyword 过滤")
    void pageModels_shouldFilterByKeyword() {
        service.create(createReq("alpha-model", "openai", TEST_API_KEY));
        service.create(createReq("beta-model", "openai", TEST_API_KEY));
        service.create(createReq("gamma-other", "ollama", null));

        PageResult<AgentModelConfigDTO> filtered = service.pageModels(new PageParam(), "model");
        assertThat(filtered.getRecords()).hasSize(2);
        assertThat(filtered.getTotal()).isEqualTo(2);

        // 空关键字 → 全量
        PageResult<AgentModelConfigDTO> all = service.pageModels(new PageParam(), null);
        assertThat(all.getRecords()).hasSize(3);
        assertThat(all.getTotal()).isEqualTo(3);

        // 分页参数生效
        PageParam pageParam = new PageParam();
        pageParam.setPageNum(1);
        pageParam.setPageSize(2);
        PageResult<AgentModelConfigDTO> paged = service.pageModels(pageParam, null);
        assertThat(paged.getRecords()).hasSize(2);
        assertThat(paged.getTotal()).isEqualTo(3);
        assertThat(paged.getPageSize()).isEqualTo(2);
    }

    // ==================== 用例 12：M07-Step5 多Key轮询字段落库 ====================

    @Test
    @DisplayName("用例12: create/update 携带 groupKey/sort/quotaCooldownSeconds → 正确落库回读；getById DTO 只读展示新字段")
    void saveAndRead_withGroupKeyFields() {
        AgentModelSaveReqDTO req = createReq("multikey-model", "openai", TEST_API_KEY);
        req.setGroupKey("g-pool");
        req.setSort(3);
        req.setQuotaCooldownSeconds(120);

        Long id = service.create(req);

        // 实体回读：新字段正确落库
        AgentModelConfig saved = mapper.selectById(id);
        assertThat(saved.getGroupKey()).isEqualTo("g-pool");
        assertThat(saved.getSort()).isEqualTo(3);
        assertThat(saved.getQuotaCooldownSeconds()).isEqualTo(120);
        assertThat(saved.getLockedUntil()).isNull();

        // update 修改 groupKey/sort/quotaCooldownSeconds → 回读生效
        AgentModelSaveReqDTO updateReq = createReq("multikey-model", "openai", "");
        updateReq.setGroupKey("g-pool-2");
        updateReq.setSort(7);
        updateReq.setQuotaCooldownSeconds(30);
        service.update(id, updateReq);

        AgentModelConfig updated = mapper.selectById(id);
        assertThat(updated.getGroupKey()).isEqualTo("g-pool-2");
        assertThat(updated.getSort()).isEqualTo(7);
        assertThat(updated.getQuotaCooldownSeconds()).isEqualTo(30);

        // DTO 展示：groupKey/sort/quotaCooldownSeconds/lockedUntil 只读透传
        AgentModelConfigDTO dto = service.getById(id);
        assertThat(dto.getGroupKey()).isEqualTo("g-pool-2");
        assertThat(dto.getSort()).isEqualTo(7);
        assertThat(dto.getQuotaCooldownSeconds()).isEqualTo(30);
        assertThat(dto.getLockedUntil()).isNull();
    }

    // ==================== 用例 8/9/10：连通性测试 ====================

    @Test
    @DisplayName("用例8: testConnection openai 协议 → 本地假服务 200 → success=true")
    void testConnection_openai_shouldSucceed() throws Exception {
        HttpServer server = startServer(200, new AtomicBoolean(false));
        try {
            int port = server.getAddress().getPort();
            Long id = service.create(createReqWithUrl("openai-model", "openai", TEST_API_KEY,
                    "http://127.0.0.1:" + port));

            AgentModelTestConnectionRespDTO resp = service.testConnection(id);

            assertThat(resp.isSuccess()).isTrue();
            assertThat(resp.getMessage()).isNotBlank();
            assertThat(resp.getLatencyMs()).isGreaterThanOrEqualTo(0);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("用例9: testConnection ollama 协议 → 请求不含 Authorization header，success=true")
    void testConnection_ollama_shouldNotSendAuthHeader() throws Exception {
        AtomicBoolean sawAuthHeader = new AtomicBoolean(false);
        HttpServer server = startServer(200, sawAuthHeader);
        try {
            int port = server.getAddress().getPort();
            Long id = service.create(createReqWithUrl("ollama-model", "ollama", "ollama-secret-key",
                    "http://127.0.0.1:" + port));

            AgentModelTestConnectionRespDTO resp = service.testConnection(id);

            assertThat(resp.isSuccess()).isTrue();
            assertThat(sawAuthHeader.get())
                    .as("ollama 协议探测请求不应携带 Authorization 头")
                    .isFalse();
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("用例10: testConnection 连接拒绝（未监听端口）→ success=false, message 非空, latencyMs>=0")
    void testConnection_unreachable_shouldReturnFailure() throws Exception {
        int unusedPort = findUnusedPort();
        Long id = service.create(createReqWithUrl("down-model", "openai", TEST_API_KEY,
                "http://127.0.0.1:" + unusedPort));

        AgentModelTestConnectionRespDTO resp = service.testConnection(id);

        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.getMessage()).isNotBlank();
        assertThat(resp.getLatencyMs()).isGreaterThanOrEqualTo(0);
    }

    // ==================== 用例 11：目标不存在 ====================

    @Test
    @DisplayName("用例11: testConnection 目标不存在的 id → 抛 NOT_FOUND 业务异常")
    void testConnection_unknownId_shouldThrow() {
        assertThatThrownBy(() -> service.testConnection(999999L))
                .isInstanceOf(BaseException.class)
                .satisfies(ex -> assertThat(((BaseException) ex).getCode())
                        .isEqualTo(CommonErrorCode.NOT_FOUND.getCode()));
    }

    // ==================== 本地假 HTTP 服务 ====================

    private static HttpServer startServer(int status, AtomicBoolean sawAuthHeader) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            if (exchange.getRequestHeaders().getFirst("Authorization") != null) {
                sawAuthHeader.set(true);
            }
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
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

    private AgentModelSaveReqDTO createReqWithUrl(String name, String protocolType, String apiKey, String baseUrl) {
        AgentModelSaveReqDTO req = createReq(name, protocolType, apiKey);
        req.setBaseUrl(baseUrl);
        return req;
    }

    // ==================== 组合测试配置 ====================

    /** 测试加密密钥：32 字节 "0123456789abcdef0123456789abcdef" 的 Base64 */
    static final String TEST_BASE64_KEY =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @Configuration
    @MapperScan("com.sw.ck.agent.mapper")
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        public DataSource dataSource() {
            return DataSourceBuilder.create()
                    .url("jdbc:h2:mem:agentmodel;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
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

        @Bean
        public AgentModelConfigService agentModelConfigService(AesGcmCipher aesGcmCipher) {
            return new AgentModelConfigServiceImpl(aesGcmCipher);
        }
    }
}
