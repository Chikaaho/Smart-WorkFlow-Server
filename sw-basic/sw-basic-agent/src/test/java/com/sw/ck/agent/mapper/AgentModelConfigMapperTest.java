package com.sw.ck.agent.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.sw.ck.agent.entity.AgentModelConfig;
import com.sw.ck.common.config.mybatis.CommonMetaObjectHandler;
import com.sw.ck.common.config.mybatis.tenant.CommonTenantLineHandler;
import com.sw.ck.common.config.mybatis.tenant.TenantProperties;
import com.sw.ck.common.crypto.AesGcmCipher;
import com.sw.ck.common.datascope.DataScopeType;
import com.sw.ck.common.security.LoginContextProvider;
import com.sw.ck.security.holder.LoginUser;
import com.sw.ck.security.holder.LoginUserHolder;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AgentModelConfigMapper} 候选查询语义测试（M07-Step5 多Key轮询/额度限流）。
 * <p>
 * 对应执行方案 §9.1 候选查询 5 用例与 §5 V3 spike（{@code .eq(enabled, 1)} 数字字面量在
 * H2 下不抛 SMALLINT/BOOLEAN 比较异常）。查询条件与
 * {@code AgentOrchestrationServiceImpl.findNextCandidate} 同构（同 groupKey、enabled=1、
 * 未锁定或已过期视为可用、排除已试 id，按 sort 升序 + id 升序保证确定性）。
 * </p>
 * <p>
 * 策略与 {@code AgentModelConfigServiceImplTest} 同款：{@code @SpringBootTest} + H2
 * （TestConfig 组合装配）+ {@code @Transactional} 回滚；建表 DDL = V19 + V24（含 4 个
 * 多Key轮询新列与 group 索引）。
 * </p>
 */
@SpringBootTest(
        classes = AgentModelConfigMapperTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.autoconfigure.exclude=com.sw.ck.common.config.mybatis.MybatisPlusConfig,"
                        + "com.sw.ck.common.config.redis.RedisConfig,"
                        + "com.sw.ck.security.config.SecurityAutoConfiguration,"
                        + "com.sw.ck.security.config.WebSecurityAutoConfiguration"
        }
)
@Transactional
@DisplayName("大模型接入配置 Mapper 候选查询测试（M07-Step5）")
class AgentModelConfigMapperTest {

    private static final Long TENANT_100 = 100L;
    private static final Long USER_1 = 1L;

    @Autowired
    private AgentModelConfigMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ==================== 建表（V19 + V24 H2 脚本 DDL） ====================

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

    // ==================== 用例 1：sort 排序 ====================

    @Test
    @DisplayName("用例1: 同组 3 条不同 sort → 返回 sort 最小者（数值越小优先级越高）")
    void findCandidate_ordersBySort() {
        insertConfig("a", "g1", 2, true, null);
        insertConfig("b", "g1", 0, true, null);
        insertConfig("c", "g1", 1, true, null);

        AgentModelConfig first = findCandidate("g1", Set.of(), LocalDateTime.now());

        assertThat(first).isNotNull();
        assertThat(first.getName()).isEqualTo("b");
    }

    // ==================== 用例 2：排除锁定中候选 ====================

    @Test
    @DisplayName("用例2: locked_until 未过期（now+1h）的候选被排除，返回未锁定候选")
    void findCandidate_excludesFutureLocked() {
        insertConfig("locked", "g2", 0, true, LocalDateTime.now().plusHours(1));
        insertConfig("free", "g2", 1, true, null);

        AgentModelConfig candidate = findCandidate("g2", Set.of(), LocalDateTime.now());

        assertThat(candidate).isNotNull();
        assertThat(candidate.getName()).isEqualTo("free");
    }

    // ==================== 用例 3：已过期锁定视为可用（惰性过期） ====================

    @Test
    @DisplayName("用例3: locked_until 已过期（now-1h）的候选被重新视为可用并返回（惰性过期，无清理任务）")
    void findCandidate_expiredLockIsAvailable() {
        insertConfig("expired", "g3", 0, true, LocalDateTime.now().minusHours(1));
        insertConfig("free", "g3", 1, true, null);

        AgentModelConfig candidate = findCandidate("g3", Set.of(), LocalDateTime.now());

        assertThat(candidate).isNotNull();
        assertThat(candidate.getName()).isEqualTo("expired");
    }

    // ==================== 用例 4：排除已试 id ====================

    @Test
    @DisplayName("用例4: excludeIds 排除最小 sort 候选 → 返回次小；全部排除 → null")
    void findCandidate_excludesTriedIds() {
        AgentModelConfig a = insertConfig("a", "g4", 0, true, null);
        AgentModelConfig b = insertConfig("b", "g4", 1, true, null);
        insertConfig("c", "g4", 2, true, null);

        // 排除 sort=0 的 a → 返回 sort=1 的 b
        AgentModelConfig next = findCandidate("g4", Set.of(a.getId()), LocalDateTime.now());
        assertThat(next).isNotNull();
        assertThat(next.getName()).isEqualTo("b");

        // 全部排除 → null（终止重试）
        AgentModelConfig none = findCandidate("g4", Set.of(a.getId(), b.getId()), LocalDateTime.now());
        // 注意：c 未在排除集合中，应返回 c
        assertThat(none).isNotNull();
        assertThat(none.getName()).isEqualTo("c");
    }

    // ==================== 用例 5：enabled 数字字面量（§5 V3 spike） ====================

    @Test
    @DisplayName("用例5: .eq(enabled, 1) 数字字面量查询在 H2 不抛 SMALLINT/BOOLEAN 异常，enabled=0 候选被排除；同 sort 按 id 升序")
    void findCandidate_enabledDigitLiteral_noSqlError() {
        insertConfig("disabled", "g5", 0, false, null);
        AgentModelConfig d1 = insertConfig("d1", "g5", 5, true, null);
        AgentModelConfig d2 = insertConfig("d2", "g5", 5, true, null);

        // V3：数字字面量 .eq(enabled, 1) 在 H2 下不抛 SMALLINT/BOOLEAN 比较异常（74fc415 先例语义）
        AgentModelConfig candidate = findCandidate("g5", Set.of(), LocalDateTime.now());

        assertThat(candidate).isNotNull();
        // enabled=0 被排除；同 sort=5 的两条按 id 升序 → 先插入的 d1（雪花 id 更小）
        assertThat(candidate.getName()).isEqualTo("d1");
        // 排除 d1 后 → d2（同 sort 按 id 升序确定性）
        AgentModelConfig next = findCandidate("g5", Set.of(d1.getId()), LocalDateTime.now());
        assertThat(next).isNotNull();
        assertThat(next.getName()).isEqualTo("d2");
        assertThat(d1.getId()).isLessThan(d2.getId());
    }

    // ==================== 候选查询（与 ServiceImpl.findNextCandidate 同构） ====================

    private AgentModelConfig findCandidate(String groupKey, Set<Long> excludeIds, LocalDateTime now) {
        List<AgentModelConfig> candidates = mapper.selectList(
                Wrappers.<AgentModelConfig>lambdaQuery()
                        .eq(AgentModelConfig::getGroupKey, groupKey)
                        .eq(AgentModelConfig::getEnabled, 1)
                        .notIn(!excludeIds.isEmpty(), AgentModelConfig::getId, excludeIds)
                        .and(w -> w.isNull(AgentModelConfig::getLockedUntil)
                                .or().le(AgentModelConfig::getLockedUntil, now))
                        .orderByAsc(AgentModelConfig::getSort)
                        .orderByAsc(AgentModelConfig::getId));
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private AgentModelConfig insertConfig(String name, String groupKey, Integer sort,
                                          Boolean enabled, LocalDateTime lockedUntil) {
        AgentModelConfig entity = new AgentModelConfig();
        entity.setName(name);
        entity.setProtocolType("openai");
        entity.setBaseUrl("http://localhost:1");
        entity.setModelName("gpt-4o");
        entity.setEnabled(enabled);
        entity.setGroupKey(groupKey);
        entity.setSort(sort);
        entity.setQuotaCooldownSeconds(60);
        entity.setLockedUntil(lockedUntil);
        mapper.insert(entity);
        return entity;
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
                    .url("jdbc:h2:mem:agentmodelmapper;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
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
    }
}
