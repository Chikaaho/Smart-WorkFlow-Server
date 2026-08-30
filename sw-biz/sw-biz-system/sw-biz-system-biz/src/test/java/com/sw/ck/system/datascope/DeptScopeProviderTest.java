package com.sw.ck.system.datascope;

import com.sw.ck.common.datascope.DataScopeType;
import com.sw.ck.common.datascope.DeptScopeProvider;
import com.sw.ck.common.security.LoginContextProvider;
import com.sw.ck.security.holder.LoginUser;
import com.sw.ck.security.holder.LoginUserHolder;
import com.sw.ck.system.entity.SysDept;
import com.sw.ck.system.mapper.SysDeptMapper;
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

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DeptScopeProviderImpl} 行为验证（本部门及以下）。
 * <p>
 * 覆盖：单层/多层后代、不含自身、叶子部门、部门不存在、null 入参、
 * parent_id 成环（含自引用）时遍历必须终止。
 * </p>
 */
@SpringBootTest(
        classes = DeptScopeProviderTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:testdb_deptscope;MODE=PostgreSQL",
                "spring.sql.init.schema-locations=classpath:db/schema-datascope-h2.sql",
                "spring.sql.init.data-locations=classpath:db/data-datascope-h2.sql"
        }
)
@ActiveProfiles("test")
class DeptScopeProviderTest {

    @Autowired
    private DeptScopeProvider deptScopeProvider;

    @Autowired
    private SysDeptMapper sysDeptMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // 显式设置 super tenant 上下文，使 TenantLineHandler 注入 tenant_id=0
        LoginUser user = new LoginUser();
        user.setUserId(1L);
        user.setTenantId(0L);
        LoginUserHolder.set(user);

        jdbcTemplate.update("DELETE FROM sys_dept"); // 物理清空，避免逻辑删除行占用主键
        seedDept(1L, 0L, "总部");
        seedDept(11L, 1L, "研发部");
        seedDept(111L, 11L, "后端组");
        seedDept(112L, 11L, "前端组");
        seedDept(12L, 1L, "市场部");
        seedDept(2L, 0L, "分公司");
        seedDept(21L, 2L, "分公司-销售");
        seedDept(31L, 32L, "环-甲");
        seedDept(32L, 31L, "环-乙");
        seedDept(41L, 41L, "自引用");
    }

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    private void seedDept(Long id, Long parentId, String name) {
        SysDept dept = new SysDept();
        dept.setId(id);
        dept.setParentId(parentId);
        dept.setName(name);
        dept.setCode("D" + id);
        sysDeptMapper.insert(dept);
    }

    @Test
    void listChildDeptIds_should_return_all_descendants_excluding_self() {
        List<Long> ids = deptScopeProvider.listChildDeptIds(1L);

        assertThat(ids)
                .as("部门 1 的全部后代应为 {11, 12, 111, 112}，不含自身")
                .containsExactlyInAnyOrder(11L, 12L, 111L, 112L);
    }

    @Test
    void listChildDeptIds_should_return_immediate_and_deep_children() {
        assertThat(deptScopeProvider.listChildDeptIds(11L))
                .as("部门 11 的后代应为 {111, 112}")
                .containsExactlyInAnyOrder(111L, 112L);
    }

    @Test
    void listChildDeptIds_should_return_single_child() {
        assertThat(deptScopeProvider.listChildDeptIds(2L))
                .as("部门 2 的后代应为 {21}")
                .containsExactlyInAnyOrder(21L);
    }

    @Test
    void listChildDeptIds_should_return_empty_for_leaf_dept() {
        assertThat(deptScopeProvider.listChildDeptIds(21L))
                .as("叶子部门应无后代")
                .isEmpty();
    }

    @Test
    void listChildDeptIds_should_return_empty_for_nonexistent_dept() {
        assertThat(deptScopeProvider.listChildDeptIds(999L))
                .as("不存在的部门应返回空")
                .isEmpty();
    }

    @Test
    void listChildDeptIds_should_return_empty_for_null() {
        assertThat(deptScopeProvider.listChildDeptIds(null))
                .as("null 入参应返回空")
                .isEmpty();
    }

    @Test
    void listChildDeptIds_should_terminate_on_parent_cycle() {
        List<Long> ids = deptScopeProvider.listChildDeptIds(31L);

        assertThat(ids)
                .as("parent_id 成环时遍历应终止，且不重复返回节点")
                .containsExactlyInAnyOrder(32L);
    }

    @Test
    void listChildDeptIds_should_terminate_on_self_reference() {
        List<Long> ids = deptScopeProvider.listChildDeptIds(41L);

        assertThat(ids)
                .as("parent_id 自引用时应终止且不包含自身")
                .isEmpty();
    }

    @Test
    void active_bean_should_be_deptScopeProviderImpl() {
        // 验证 sw-biz-system 的实现 Bean 覆盖了 MybatisPlusConfig 的 noop 兜底
        assertThat(deptScopeProvider)
                .as("当前上下文中生效的 DeptScopeProvider 应为实现类而非 noop 兜底")
                .isInstanceOf(DeptScopeProviderImpl.class);
    }

    // ==================== 测试上下文配置 ====================

    @Configuration
    @EnableAutoConfiguration
    @ComponentScan({"com.sw.ck.system.service.impl", "com.sw.ck.system.datascope"})
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

        @Bean
        public PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(10);
        }
    }
}
