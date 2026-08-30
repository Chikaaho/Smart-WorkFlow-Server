package com.sw.ck.system.dict;

import com.sw.ck.common.datascope.DataScopeType;
import com.sw.ck.common.security.LoginContextProvider;
import com.sw.ck.security.holder.LoginUser;
import com.sw.ck.security.holder.LoginUserHolder;
import com.sw.ck.system.entity.SysDictType;
import com.sw.ck.system.mapper.SysDictTypeMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 逻辑删除（@TableLogic）往返验证测试。
 * <p>
 * 验证 MyBatis-Plus {@code @TableLogic} 注解在 {@link SysDictType}
 *（继承 {@link com.sw.ck.common.entity.BaseEntity}，含 deleted 字段）上的完整行为：
 * <ol>
 *   <li>插入时自动设置 deleted=0</li>
 *   <li>deleteById 后该行 deleted=1（逻辑删除，非物理删除）</li>
 *   <li>常规 selectById 查不到已逻辑删除的记录（MP 自动追加 {@code AND deleted = 0}）</li>
 *   <li>逻辑删除后，以相同业务键（code）可再插入新记录，不碰撞唯一索引</li>
 * </ol>
 * </p>
 *
 * <p>其中第 4 项依赖于 V13 迁移——将 {@code uk_sys_dict_type_code} 从
 * 全值唯一改为 {@code WHERE deleted = 0} 条件唯一。</p>
 */
@SpringBootTest(
        classes = LogicalDeleteTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.datasource.url=jdbc:h2:mem:testdb_logical_del;MODE=PostgreSQL"}
)
@ActiveProfiles("test")
class LogicalDeleteTest {

    @Autowired
    private SysDictTypeMapper dictTypeMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 测试用唯一 code（每次新建实例使用不同值避免跨方法干扰） */
    private String testCode;

    @BeforeEach
    void setUp() {
        // 显式设置 super tenant 上下文
        LoginUser user = new LoginUser();
        user.setUserId(1L);
        user.setTenantId(0L);
        LoginUserHolder.set(user);

        // 每次测试生成新 code，避免 seed data 冲突
        testCode = "test_logical_delete_" + System.nanoTime();
    }

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    @Test
    void logicalDelete_should_set_deleted_1_and_hide_from_select() {
        // ========== 1. 插入 ==========
        SysDictType entity = new SysDictType();
        entity.setName("逻辑删除测试");
        entity.setCode(testCode);
        entity.setStatus(0);
        dictTypeMapper.insert(entity);

        Long insertedId = entity.getId();
        assertThat(insertedId).as("插入后 id 应自动生成").isNotNull();

        // ========== 2. 插入后 deleted 应为 0 ==========
        SysDictType inserted = dictTypeMapper.selectById(insertedId);
        assertThat(inserted)
                .as("插入后可正常查询")
                .isNotNull();
        // 基类继承的 deleted 字段通过 getter 访问
        assertThat(inserted.getDeleted())
                .as("插入时 deleted 默认为 0")
                .isEqualTo(0);

        // ========== 3. 逻辑删除 ==========
        int rows = dictTypeMapper.deleteById(insertedId);
        assertThat(rows).as("deleteById 应影响 1 行").isEqualTo(1);

        // ========== 4. 验证 DB 中 deleted = 1（使用 JdbcTemplate 绕过 @TableLogic 自动过滤）==========
        Integer deletedValue = jdbcTemplate.queryForObject(
                "SELECT deleted FROM sys_dict_type WHERE id = ?",
                Integer.class,
                insertedId
        );
        assertThat(deletedValue)
                .as("逻辑删除后 deleted 应为 1")
                .isNotNull()
                .isEqualTo(1);

        // ========== 5. MP 标准查询应过滤掉逻辑删除记录 ==========
        SysDictType afterDelete = dictTypeMapper.selectById(insertedId);
        assertThat(afterDelete)
                .as("selectById 在逻辑删除后应返回 null（MP 自动追加 AND deleted = 0）")
                .isNull();

        // ========== 6. 以相同 code 再插入，不应撞唯一索引 ==========
        SysDictType recreated = new SysDictType();
        recreated.setName("逻辑删除重建测试");
        recreated.setCode(testCode);   // 与软删记录相同的 code
        recreated.setStatus(0);
        dictTypeMapper.insert(recreated);

        Long newId = recreated.getId();
        assertThat(newId).as("新记录应成功插入、不抛唯一约束异常").isNotNull();

        // 验证新记录可正常查询
        SysDictType queried = dictTypeMapper.selectById(newId);
        assertThat(queried)
                .as("新记录可正常查询")
                .isNotNull();
        assertThat(queried.getCode())
                .as("新记录 code 与已软删记录相同")
                .isEqualTo(testCode);
        assertThat(queried.getDeleted())
                .as("新记录 deleted 应为 0")
                .isEqualTo(0);

        // 验证仍存在两条记录（一条软删 + 一条正常，绕过 @TableLogic 过滤）
        Long totalCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_dict_type WHERE code = ?",
                Long.class,
                testCode
        );
        assertThat(totalCount)
                .as("同一 code 应有 2 行（1 条软删 + 1 条正常）")
                .isEqualTo(2);
    }

    // ==================== 测试上下文配置 ====================

    @Configuration
    @EnableAutoConfiguration
    @ComponentScan("com.sw.ck.system.service.impl")
    static class TestConfig {

        @Bean
        public static MapperScannerConfigurer mapperScannerConfigurer() {
            MapperScannerConfigurer configurer = new MapperScannerConfigurer();
            configurer.setBasePackage("com.sw.ck.system.mapper");
            return configurer;
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
                    return true;
                }
            };
        }

        /**
         * PasswordEncoder（被 SysUserServiceImpl 依赖）。
         * 因 SystemAutoConfiguration 已被排除，在此显式提供。
         */
        @Bean
        public PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(10);
        }
    }
}
