package com.sw.ck.bootstrap;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * BPM 迁移链纳入真实 H2 全链 Flyway 验证的永久测试（不启动 Spring 上下文）。
 * <p>
 * 使用独立内存库 + 独立 Flyway 实例，7 个 locations 与 {@code application.yml}
 * 完全一致（{vendor} 按 H2 连接解析为 h2）。全链共 30 条迁移
 * （28 条原冒烟口径 + BPM V8/V14 两枚）。
 * </p>
 * <p>
 * H2 不支持 PG 的 partial unique index，BPM V8 在 H2 侧用生成列
 * {@code active_key} + 唯一索引 {@code uk_sw_bpm_binding_active} 等价实现
 * 「同租户同 form_key 仅一条 active=true」约束。本测试除迁移计数/校验外，
 * 还用 JDBC 对绑定语义做正反例验证（H2 唯一约束冲突 SQLState=23505）。
 * </p>
 */
@DisplayName("BPM 全链 H2 Flyway 迁移 + 绑定语义验证")
class FlywayFullChainH2Test {

    private static final String URL = "jdbc:h2:mem:flyway_full_chain;DB_CLOSE_DELAY=-1";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    /**
     * 与 application.yml flyway.locations 完全一致的 7 个位置。
     * <p>
     * 注意：{vendor} 占位符并非由 flyway-core 解析，而是 Spring Boot
     * {@code FlywayAutoConfiguration$LocationResolver} 按 JDBC 驱动替换
     * （flyway-core 11.3.4 实测不识别 {vendor}）。本测试不启动 Spring 上下文，
     * 故在 {@link #migrateFullChain()} 中按 H2 连接显式解析为 h2，
     * 目录结构与 application.yml 一一对应。
     * </p>
     */
    private static final String[] APP_LOCATIONS = {
            "classpath:db/migration/{vendor}",
            "classpath:db/migration/bpm/{vendor}",
            "classpath:db/migration/notify/{vendor}",
            "classpath:db/migration/form/{vendor}",
            "classpath:db/migration/storage/{vendor}",
            "classpath:db/migration/job/{vendor}",
            "classpath:db/migration/agent/{vendor}"
    };

    private static Flyway flyway;

    @BeforeAll
    static void migrateFullChain() {
        String[] locations = Arrays.stream(APP_LOCATIONS)
                .map(location -> location.replace("{vendor}", "h2"))
                .toArray(String[]::new);
        flyway = Flyway.configure()
                .dataSource(URL, USER, PASSWORD)
                .locations(locations)
                .load();
        MigrateResult result = flyway.migrate();
        assertTrue(result.success, "全链迁移应成功");
        assertEquals(30, result.migrationsExecuted,
                "全链迁移计数应为 30（28 条原口径 + BPM V8/V14 两枚），实际: " + result.migrationsExecuted);
    }

    @Test
    @DisplayName("全链迁移后：info().applied() 共 30 条，包含 BPM V8/V14")
    void appliedMigrationCount_shouldBe30() {
        org.flywaydb.core.api.MigrationInfo[] applied = flyway.info().applied();
        assertEquals(30, applied.length, "已应用迁移数应为 30");
        boolean v8Seen = false;
        boolean v14Seen = false;
        for (org.flywaydb.core.api.MigrationInfo info : applied) {
            if ("8".equals(info.getVersion().getVersion())) {
                v8Seen = true;
            }
            if ("14".equals(info.getVersion().getVersion())) {
                v14Seen = true;
            }
        }
        assertTrue(v8Seen, "BPM V8 应已应用");
        assertTrue(v14Seen, "BPM V14 应已应用");
    }

    @Test
    @DisplayName("全链迁移后：再次 validate() 通过（无校验和/缺失迁移问题）")
    void validate_shouldPass() {
        flyway.validate();
    }

    @Test
    @DisplayName("sw_bpm_form_binding 表、uk_sw_bpm_binding_active 索引与 active_key 生成列存在")
    void bindingTableIndexAndGeneratedColumn_shouldExist() throws SQLException {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            DatabaseMetaData md = conn.getMetaData();

            try (ResultSet rs = md.getTables(null, null, "SW_BPM_FORM_BINDING", new String[]{"TABLE"})) {
                assertTrue(rs.next(), "sw_bpm_form_binding 表应存在");
            }

            boolean indexFound = false;
            try (ResultSet rs = md.getIndexInfo(null, null, "SW_BPM_FORM_BINDING", false, false)) {
                while (rs.next()) {
                    if ("UK_SW_BPM_BINDING_ACTIVE".equalsIgnoreCase(rs.getString("INDEX_NAME"))) {
                        indexFound = true;
                        break;
                    }
                }
            }
            assertTrue(indexFound, "唯一索引 uk_sw_bpm_binding_active 应存在");

            boolean columnFound = false;
            try (ResultSet rs = md.getColumns(null, null, "SW_BPM_FORM_BINDING", "ACTIVE_KEY")) {
                columnFound = rs.next();
            }
            assertTrue(columnFound, "生成列 active_key 应存在");
        }
    }

    @Test
    @DisplayName("正例：插入 active=true 成功；同租户同 form_key 第二条 active=true 冲突（SQLState=23505）")
    void duplicateActiveBinding_shouldFailWith23505() throws SQLException {
        insertBinding(1001L, 100L, "form_chain", "def_chain", true);

        expectUniqueViolation(() -> insertBinding(1002L, 100L, "form_chain", "def_chain2", true));
    }

    @Test
    @DisplayName("正例：同租户同 form_key 多条 active=false 历史记录可共存")
    void multipleInactiveBindings_shouldCoexist() throws SQLException {
        insertBinding(2001L, 200L, "form_history", "def_old1", false);
        insertBinding(2002L, 200L, "form_history", "def_old2", false);
        insertBinding(2003L, 200L, "form_history", "def_old3", false);

        assertEquals(3, countRows(
                "SELECT COUNT(*) FROM sw_bpm_form_binding WHERE tenant_id = 200 AND form_key = 'form_history'"));
    }

    @Test
    @DisplayName("正例：不同 tenant_id 同 form_key 各一条 active=true 可共存（租户隔离）")
    void activeBindings_differentTenants_shouldCoexist() throws SQLException {
        insertBinding(3001L, 301L, "form_tenant", "def_chain", true);
        insertBinding(3002L, 302L, "form_tenant", "def_chain", true);
        insertBinding(3003L, 303L, "form_tenant", "def_chain", true);

        assertEquals(3, countRows(
                "SELECT COUNT(*) FROM sw_bpm_form_binding WHERE active = true AND form_key = 'form_tenant'"));
    }

    @Test
    @DisplayName("正例：先停用 active=true→false，再插入新 active=true 成功（启停切换）")
    void deactivateThenInsertNewActive_shouldSucceed() throws SQLException {
        insertBinding(4001L, 400L, "form_switch", "def_a", true);
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE sw_bpm_form_binding SET active = false WHERE id = 4001");
        }

        insertBinding(4002L, 400L, "form_switch", "def_b", true);

        assertEquals(1, countRows(
                "SELECT COUNT(*) FROM sw_bpm_form_binding WHERE tenant_id = 400 AND form_key = 'form_switch' AND active = true"));
    }

    @Test
    @DisplayName("反例：已有 active=true 时，把另一条 active=false 更新为 true 被拒绝（SQLState=23505）")
    void updateInactiveToActive_withExistingActive_shouldFail() throws SQLException {
        insertBinding(5001L, 500L, "form_update", "def_a", false);
        insertBinding(5002L, 500L, "form_update", "def_b", true);

        expectUniqueViolation(() -> {
            try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
                 Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("UPDATE sw_bpm_form_binding SET active = true WHERE id = 5001");
            }
        });
    }

    // ==================== 辅助方法 ====================

    @FunctionalInterface
    private interface SqlRunnable {
        void run() throws SQLException;
    }

    /** 断言唯一约束冲突：H2 唯一索引冲突 SQLState 为 23505，用 try/catch 显式捕获断言。 */
    private void expectUniqueViolation(SqlRunnable action) throws SQLException {
        try {
            action.run();
            fail("预期唯一约束冲突，但语句执行成功");
        } catch (SQLException e) {
            assertEquals("23505", e.getSQLState(),
                    "唯一约束冲突 SQLState 应为 23505，实际: " + e.getSQLState() + "，消息: " + e.getMessage());
        }
    }

    private void insertBinding(Long id, Long tenantId, String formKey, String processDefKey, boolean active)
            throws SQLException {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO sw_bpm_form_binding (id, tenant_id, form_key, process_def_key, active) VALUES ("
                    + id + ", " + tenantId + ", '" + formKey + "', '" + processDefKey + "', " + active + ")");
        }
    }

    private int countRows(String sql) throws SQLException {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
