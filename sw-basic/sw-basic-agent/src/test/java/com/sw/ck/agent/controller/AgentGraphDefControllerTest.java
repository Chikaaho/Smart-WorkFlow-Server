package com.sw.ck.agent.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw.ck.agent.dto.graph.ProcessGraph;
import com.sw.ck.agent.entity.AgentGraphDef;
import com.sw.ck.agent.mapper.AgentGraphDefMapper;
import com.sw.ck.agent.service.AgentGraphDefService;
import com.sw.ck.agent.service.impl.AgentGraphDefServiceImpl;
import com.sw.ck.common.config.mybatis.CommonMetaObjectHandler;
import com.sw.ck.common.datascope.DataScopeType;
import com.sw.ck.common.exception.BaseException;
import com.sw.ck.common.response.R;
import com.sw.ck.common.security.LoginContextProvider;
import com.sw.ck.security.cache.LoginUserCacheService;
import com.sw.ck.security.cache.LoginUserLoader;
import com.sw.ck.security.config.SecurityProperties;
import com.sw.ck.security.filter.JwtAuthenticationFilter;
import com.sw.ck.security.handler.RestAccessDeniedHandler;
import com.sw.ck.security.handler.RestAuthenticationEntryPoint;
import com.sw.ck.security.holder.LoginUser;
import com.sw.ck.security.holder.LoginUserHolder;
import com.sw.ck.security.jwt.JwtProperties;
import com.sw.ck.security.jwt.JwtTokenProvider;
import com.sw.ck.security.jwt.JwtTokenProviderImpl;
import com.sw.ck.security.spi.UserDetailsProvider;
import com.sw.ck.security.support.PermissionService;
import jakarta.servlet.Filter;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link AgentGraphDefController} 测试（M07 Step7 §11.1 表格 8 用例）。
 * <p>
 * 策略：{@code @SpringBootTest}（MOCK 环境）+ 手动 {@code MockMvc}（webAppContextSetup），
 * 装配真实的 {@link JwtAuthenticationFilter} + {@link SecurityFilterChain} +
 * {@code @EnableMethodSecurity}（来自 TestConfig），请求携带真实 JWT token，
 * 用户数据由 {@link StubUserDetailsProvider} 按 userId 提供（权限组合可控）。
 * 装配模式完全复制 {@code AgentModelControllerTest}。
 * </p>
 * <p>
 * 注意：业务异常（BaseException）经 {@code GlobalExceptionHandler} 走"HTTP 200 + body.code"
 * 模式（全局设计如此），因此"删除后详情"断言的是 HTTP 200 + body.code=404，而非 HTTP 404。
 * </p>
 */
@SpringBootTest(
        classes = AgentGraphDefControllerTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.autoconfigure.exclude=com.sw.ck.common.config.mybatis.MybatisPlusConfig,"
                        + "com.sw.ck.common.config.redis.RedisConfig,"
                        + "com.sw.ck.security.config.SecurityAutoConfiguration,"
                        + "com.sw.ck.security.config.WebSecurityAutoConfiguration,"
                        + "org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration",
                "spring.ai.openai.api-key=test-dummy"
        }
)
@DisplayName("Agent 图定义 Controller 测试（权限沿用 model view/manage）")
class AgentGraphDefControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AgentGraphDefMapper mapper;

    @Autowired
    private AgentGraphDefService service;

    // ==================== 建表（V25 H2 脚本 DDL） ====================

    @BeforeAll
    static void createTables(@Autowired JdbcTemplate jt) {
        jt.execute("""
                CREATE TABLE IF NOT EXISTS sw_agent_graph_def (
                    id           BIGINT NOT NULL PRIMARY KEY,
                    graph_key    VARCHAR(100) NOT NULL,
                    name         VARCHAR(200) NOT NULL,
                    def_version  INT NOT NULL DEFAULT 1,
                    status       VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
                    graph_json   CLOB,
                    create_time  TIMESTAMP,
                    create_by    VARCHAR(64),
                    update_time  TIMESTAMP,
                    update_by    VARCHAR(64),
                    deleted      SMALLINT NOT NULL DEFAULT 0,
                    tenant_id    BIGINT NOT NULL DEFAULT 0,
                    version      BIGINT NOT NULL DEFAULT 0
                )
                """);
        jt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_sw_agent_graph_key ON sw_agent_graph_def (tenant_id, graph_key)");
        jt.execute("CREATE INDEX IF NOT EXISTS idx_sw_agent_graph_tenant_deleted ON sw_agent_graph_def (tenant_id, deleted)");
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM sw_agent_graph_def");
        LoginUserHolder.clear();
    }

    /**
     * MockMvc 请求由 JwtAuthenticationFilter 在请求结束时清理 LoginUserHolder，
     * perform() 返回后测试线程无登录上下文；租户拦截器在 tenant_id 为 NULL 时
     * 注入 WHERE tenant_id = NULL（恒 false），mapper 查询将 0 行。
     * 因此所有 mapper/JdbcTemplate 断言前必须重建登录上下文（tenant 100，与
     * JWT 请求内用户一致）。
     */
    private void setDbLoginContext() {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(2L);
        loginUser.setTenantId(100L);
        loginUser.setUsername("test_user");
        loginUser.setSuperAdmin(false);
        LoginUserHolder.set(loginUser);
    }

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    private String bearerToken(Long userId) {
        return "Bearer " + jwtTokenProvider.generateToken(userId);
    }

    private String createGraphJson(String name) {
        return """
                {"graphKey":"agent_test_key","name":"%s","version":1,
                 "elements":[
                   {"id":"node_start","kind":"node","type":"START","config":{},"style":{"x":100,"y":300}},
                   {"id":"node_end","kind":"node","type":"END","config":{},"style":{"x":700,"y":300}},
                   {"id":"edge_1","kind":"edge","source":"node_start","target":"node_end","config":{},"style":{}}
                 ],
                 "canvas":{}}
                """.formatted(name);
    }

    private Long createGraphAs(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/agent/graph-defs")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("code").asInt()).isZero();
        return body.get("data").asLong();
    }

    // ==================== 用例 1：无 view 权限 → 403 ====================

    @Test
    @DisplayName("用例1: 无 agent:model:view 权限访问 GET /agent/graph-defs → 403")
    void page_withoutViewPermission_shouldReturn403() throws Exception {
        MvcResult result = mockMvc.perform(get("/agent/graph-defs")
                        .header("Authorization", bearerToken(1L)))
                .andExpect(status().isForbidden())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("code").asInt()).isEqualTo(403);
        assertThat(body.get("msg").asText()).isNotBlank();
    }

    // ==================== 用例 2：manage 权限创建 ====================

    @Test
    @DisplayName("用例2: 具备 agent:model:manage 权限调用 POST /agent/graph-defs → 创建成功返回 id")
    void create_withManagePermission_shouldSucceed() throws Exception {
        Long id = createGraphAs(bearerToken(2L), "controller-图");

        setDbLoginContext();
        AgentGraphDef saved = mapper.selectById(id);
        assertThat(saved).isNotNull();
        assertThat(saved.getGraphKey()).startsWith("agent_");
        assertThat(saved.getStatus()).isEqualTo("DRAFT");
        assertThat(saved.getDefVersion()).isEqualTo(1);
    }

    // ==================== 用例 3：manage 权限保存草稿 ====================

    @Test
    @DisplayName("用例3: PUT /agent/graph-defs/{id}/graph 草稿保存 → 200 且 graph_json 覆盖")
    void saveDraftGraph_withManagePermission_shouldSucceed() throws Exception {
        Long id = createGraphAs(bearerToken(2L), "草稿-测试");

        mockMvc.perform(put("/agent/graph-defs/" + id + "/graph")
                        .header("Authorization", bearerToken(2L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createGraphJson("草稿-测试")))
                .andExpect(status().isOk());

        setDbLoginContext();
        AgentGraphDef saved = mapper.selectById(id);
        assertThat(saved.getGraphJson()).contains("node_start").contains("agent_test_key");
        assertThat(saved.getStatus()).as("草稿保存不得改变状态").isEqualTo("DRAFT");
    }

    // ==================== 用例 4：manage 权限发布 ====================

    @Test
    @DisplayName("用例4: POST /agent/graph-defs/{id}/publish → 200 且 defVersion=2、PUBLISHED")
    void publish_withManagePermission_shouldSucceed() throws Exception {
        Long id = createGraphAs(bearerToken(2L), "发布-测试");

        MvcResult result = mockMvc.perform(post("/agent/graph-defs/" + id + "/publish")
                        .header("Authorization", bearerToken(2L)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("code").asInt()).isZero();
        assertThat(body.get("data").get("defVersion").asInt()).isEqualTo(2);
        assertThat(body.get("data").get("status").asText()).isEqualTo("PUBLISHED");
        assertThat(body.get("data").get("graphKey").asText()).startsWith("agent_");
    }

    // ==================== 用例 5：superAdmin 详情回显 ====================

    @Test
    @DisplayName("用例5: GET /agent/graph-defs/{id} → 200 + 图对象 elements 回显（superAdmin 绕过权限）")
    void getGraph_shouldEchoGraph() throws Exception {
        Long id = createGraphAs(bearerToken(3L), "详情-测试");

        MvcResult result = mockMvc.perform(get("/agent/graph-defs/" + id)
                        .header("Authorization", bearerToken(3L)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("code").asInt()).isZero();
        assertThat(body.get("data").get("elements")).hasSize(3);
        assertThat(body.get("data").get("elements").get(0).get("type").asText()).isEqualTo("START");
    }

    // ==================== 用例 6：分页列表 ====================

    @Test
    @DisplayName("用例6: GET /agent/graph-defs → 200 + 分页结构且不含 graphJson 大字段")
    void pageDefs_shouldReturnPaged() throws Exception {
        createGraphAs(bearerToken(3L), "列表-1");
        createGraphAs(bearerToken(3L), "列表-2");

        MvcResult result = mockMvc.perform(get("/agent/graph-defs")
                        .header("Authorization", bearerToken(3L)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("code").asInt()).isZero();
        assertThat(body.get("data").get("total").asInt()).isEqualTo(2);
        assertThat(body.get("data").get("records").get(0).has("graphJson"))
                .as("列表不得返回 graph_json 大字段").isFalse();
        assertThat(body.get("data").get("records").get(0).get("status").asText()).isEqualTo("DRAFT");
    }

    // ==================== 用例 7：删除后详情 → body.code=404 ====================

    @Test
    @DisplayName("用例7: DELETE 后 GET 详情 → HTTP 200 + body.code=404（业务异常 200+code 模式）")
    void delete_thenGet_shouldReturnNotFoundCode() throws Exception {
        Long id = createGraphAs(bearerToken(3L), "删除-测试");

        mockMvc.perform(delete("/agent/graph-defs/" + id)
                        .header("Authorization", bearerToken(3L)))
                .andExpect(status().isOk());

        setDbLoginContext();
        assertThat(mapper.selectById(id)).as("逻辑删除后 selectById 应过滤").isNull();

        MvcResult result = mockMvc.perform(get("/agent/graph-defs/" + id)
                        .header("Authorization", bearerToken(3L)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("code").asInt()).isEqualTo(404);
    }

    // ==================== 用例 8：manage 无 view → 详情 403 ====================

    @Test
    @DisplayName("用例8: 仅 manage 无 view 权限 → GET 详情 403（两权限码互不越权）")
    void getGraph_withoutViewPermission_shouldReturn403() throws Exception {
        Long id = createGraphAs(bearerToken(2L), "越权-测试");

        mockMvc.perform(get("/agent/graph-defs/" + id)
                        .header("Authorization", bearerToken(2L)))
                .andExpect(status().isForbidden());
    }

    // ==================== 组合测试配置 ====================

    /** 按 userId 提供可控权限的 UserDetailsProvider 测试桩 */
    static class StubUserDetailsProvider implements UserDetailsProvider {

        private final Map<Long, LoginUser> users;

        StubUserDetailsProvider(Map<Long, LoginUser> users) {
            this.users = users;
        }

        @Override
        public LoginUser loadByUsername(String username) {
            return null;
        }

        @Override
        public LoginUser loadByUserId(Long userId) {
            return users.get(userId);
        }
    }

    @Configuration
    @EnableAutoConfiguration
    @MapperScan("com.sw.ck.agent.mapper")
    @EnableTransactionManagement
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestConfig {

        // ==================== 数据源 + MyBatis-Plus ====================

        @Bean
        public DataSource dataSource() {
            return DataSourceBuilder.create()
                    .url("jdbc:h2:mem:agentgraphctrl;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
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
        public TenantLineInnerInterceptor tenantLineInnerInterceptor(
                LoginContextProvider loginContextProvider) {
            return new TenantLineInnerInterceptor(new com.sw.ck.common.config.mybatis.tenant.CommonTenantLineHandler(
                    new com.sw.ck.common.config.mybatis.tenant.TenantProperties(), loginContextProvider));
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

        // ==================== 业务 Bean ====================

        @Bean
        public AgentGraphDefService agentGraphDefService(ObjectMapper objectMapper) {
            return new AgentGraphDefServiceImpl(objectMapper);
        }

        @Bean
        public AgentGraphDefController agentGraphDefController(AgentGraphDefService agentGraphDefService) {
            return new AgentGraphDefController(agentGraphDefService);
        }

        // ==================== JSON ====================
        // 不手动定义 ObjectMapper：@EnableAutoConfiguration 的 JacksonAutoConfiguration
        // 会提供（自动注册 JavaTimeModule，LocalDateTime 序列化所需）；手动裸 ObjectMapper
        // 会退让自动配置并导致 java.time 类型序列化失败

        // ==================== JWT / 认证 ====================

        @Bean
        public JwtProperties jwtProperties() {
            JwtProperties props = new JwtProperties();
            props.setSecret("test-jwt-secret-at-least-256-bits-long-for-hs256-algorithm");
            props.setExpireSeconds(7200);
            props.setAccessExpireSeconds(900);
            props.setRefreshExpireSeconds(604800);
            return props;
        }

        @Bean
        public JwtTokenProvider jwtTokenProvider(JwtProperties jwtProperties) {
            return new JwtTokenProviderImpl(jwtProperties);
        }

        @Bean
        public SecurityProperties securityProperties() {
            SecurityProperties props = new SecurityProperties();
            props.setTokenHeader("Authorization");
            props.setTokenPrefix("Bearer ");
            props.setPermitUrls(List.of("/auth/login"));
            return props;
        }

        @Bean
        @SuppressWarnings({"unchecked", "rawtypes"})
        public LoginUserCacheService loginUserCacheService(JwtProperties jwtProperties) {
            RedisTemplate<String, Object> mockRedis = mock(RedisTemplate.class);
            ValueOperations<String, Object> mockOps = mock(ValueOperations.class);
            when(mockRedis.opsForValue()).thenReturn(mockOps);
            when(mockOps.get(anyString())).thenReturn(null);
            return new LoginUserCacheService(mockRedis, jwtProperties) {
                @Override
                public void cache(LoginUser loginUser) {
                    // no-op: no Redis in test
                }

                @Override
                public void evict(Long userId) {
                    // no-op
                }
            };
        }

        @Bean
        @SuppressWarnings("unchecked")
        public LoginUserLoader loginUserLoader(
                UserDetailsProvider userDetailsProvider,
                LoginUserCacheService loginUserCacheService) {
            org.springframework.beans.factory.ObjectProvider<UserDetailsProvider> provider =
                    mock(org.springframework.beans.factory.ObjectProvider.class);
            when(provider.getIfAvailable()).thenReturn(userDetailsProvider);
            return new LoginUserLoader(provider, loginUserCacheService);
        }

        /** 三个测试用户：1=无权限，2=仅 manage，3=superAdmin */
        @Bean
        public UserDetailsProvider userDetailsProvider() {
            Map<Long, LoginUser> users = new HashMap<>();

            LoginUser userA = new LoginUser();
            userA.setUserId(1L);
            userA.setTenantId(100L);
            userA.setUsername("user_a");
            userA.setPermissions(List.of());
            userA.setSuperAdmin(false);
            users.put(1L, userA);

            LoginUser userB = new LoginUser();
            userB.setUserId(2L);
            userB.setTenantId(100L);
            userB.setUsername("user_b");
            userB.setPermissions(List.of("agent:model:manage"));
            userB.setSuperAdmin(false);
            users.put(2L, userB);

            LoginUser userC = new LoginUser();
            userC.setUserId(3L);
            userC.setTenantId(100L);
            userC.setUsername("super_admin");
            userC.setPermissions(List.of());
            userC.setSuperAdmin(true);
            users.put(3L, userC);

            return new StubUserDetailsProvider(users);
        }

        // ==================== 安全链（对齐 WebSecurityAutoConfiguration 装配） ====================

        @Bean("ss")
        public PermissionService permissionService() {
            return new PermissionService();
        }

        @Bean
        public RestAuthenticationEntryPoint restAuthenticationEntryPoint(ObjectMapper objectMapper) {
            return new RestAuthenticationEntryPoint(objectMapper);
        }

        @Bean
        public RestAccessDeniedHandler restAccessDeniedHandler(ObjectMapper objectMapper) {
            return new RestAccessDeniedHandler(objectMapper);
        }

        @Bean
        public JwtAuthenticationFilter jwtAuthenticationFilter(
                JwtTokenProvider jwtTokenProvider,
                LoginUserLoader loginUserLoader,
                SecurityProperties securityProperties) {
            return new JwtAuthenticationFilter(jwtTokenProvider, loginUserLoader, securityProperties);
        }

        @Bean
        public UserDetailsService noopUserDetailsService() {
            return username -> {
                throw new UsernameNotFoundException("本系统认证不经过 UserDetailsService：" + username);
            };
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                       JwtAuthenticationFilter jwtAuthenticationFilter,
                                                       RestAuthenticationEntryPoint authenticationEntryPoint,
                                                       RestAccessDeniedHandler accessDeniedHandler) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .exceptionHandling(ex -> ex
                            .authenticationEntryPoint(authenticationEntryPoint)
                            .accessDeniedHandler(accessDeniedHandler))
                    .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
            return http.build();
        }

        // ==================== MockMvc（webAppContextSetup + 真实安全链） ====================

        @Bean
        public MockMvc mockMvc(WebApplicationContext context,
                               @Qualifier("springSecurityFilterChain") Filter springSecurityFilterChain) {
            return MockMvcBuilders.webAppContextSetup(context)
                    .addFilters(springSecurityFilterChain)
                    .build();
        }

        // ==================== 业务异常处理器 ====================
        // 生产语义"HTTP 200 + body.code"：BaseException → R.fail(code, msg)。
        // 注意不能直接注册 sw-common 的 GlobalExceptionHandler——其
        // @ExceptionHandler(Exception.class) 会在 DispatcherServlet 层抢走
        // AccessDeniedException（本应由安全链 ExceptionTranslationFilter 转 403），
        // 故用局部 advice 只处理 BaseException，权限异常留给安全链。

        @RestControllerAdvice
        static class TestBusinessExceptionAdvice {

            @ExceptionHandler(BaseException.class)
            public R<Void> handleBaseException(BaseException ex) {
                return R.fail(ex.getCode(), ex.getMessage());
            }
        }

        @Bean
        public TestBusinessExceptionAdvice testBusinessExceptionAdvice() {
            return new TestBusinessExceptionAdvice();
        }
    }
}
