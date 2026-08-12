package com.sw.ck.agent.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw.ck.agent.dto.AgentGraphExecuteRespDTO;
import com.sw.ck.agent.dto.AgentGraphExecutionDTO;
import com.sw.ck.agent.dto.AgentGraphExecutionDetailDTO;
import com.sw.ck.agent.dto.AgentGraphExecutionNodeDTO;
import com.sw.ck.agent.dto.graph.GraphElement;
import com.sw.ck.agent.dto.graph.ProcessGraph;
import com.sw.ck.agent.entity.AgentGraphDef;
import com.sw.ck.agent.entity.AgentGraphExecution;
import com.sw.ck.agent.entity.AgentGraphExecutionNode;
import com.sw.ck.agent.entity.AgentModelConfig;
import com.sw.ck.agent.entity.tool.AgentToolInternalConfig;
import com.sw.ck.agent.mapper.AgentGraphDefMapper;
import com.sw.ck.agent.mapper.AgentGraphExecutionMapper;
import com.sw.ck.agent.mapper.AgentGraphExecutionNodeMapper;
import com.sw.ck.agent.mapper.AgentModelConfigMapper;
import com.sw.ck.agent.mapper.tool.AgentToolExternalConfigMapper;
import com.sw.ck.agent.mapper.tool.AgentToolInternalConfigMapper;
import com.sw.ck.agent.orchestration.AgentToolCallbackFactory;
import com.sw.ck.agent.orchestration.ChatModelFactory;
import com.sw.ck.agent.service.AgentGraphDefService;
import com.sw.ck.agent.service.AgentGraphExecutionService;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AgentGraphExecutionServiceImpl} 测试（M07-F02 Step8 §7，@SpringBootTest + H2）。
 * <p>
 * 策略与 {@code AgentGraphDefServiceImplTest} 同款组合装配（TestConfig 手动装配
 * MyBatis-Plus + 租户拦截器 + ObjectMapper），另装配 {@code ChatModelFactory}/
 * {@code AgentToolCallbackFactory} 两个 mock 与真实 {@code AesGcmCipher}（final 类不
 * mock，用测试密钥验证解密→build 全链路）。建表 DDL 与 V25/V19/V20/V21 H2 脚本对齐。
 * </p>
 */
@SpringBootTest(
        classes = AgentGraphExecutionServiceImplTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.autoconfigure.exclude=com.sw.ck.common.config.mybatis.MybatisPlusConfig,"
                        + "com.sw.ck.common.config.redis.RedisConfig,"
                        + "com.sw.ck.security.config.SecurityAutoConfiguration,"
                        + "com.sw.ck.security.config.WebSecurityAutoConfiguration"
        }
)
@Transactional
@DisplayName("Agent 图执行 Service 测试")
class AgentGraphExecutionServiceImplTest {

    /** 测试 AES 密钥（32 字节 "0123456789abcdef0123456789abcdef" 的 Base64） */
    private static final String TEST_CIPHER_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private static final Long TENANT_100 = 100L;
    private static final Long TENANT_200 = 200L;
    private static final Long USER_1 = 1L;

    @Autowired
    private AgentGraphExecutionService service;

    @Autowired
    private AgentGraphDefService graphDefService;

    @Autowired
    private AgentModelConfigMapper modelConfigMapper;

    @Autowired
    private AgentToolInternalConfigMapper internalToolMapper;

    @Autowired
    private AgentGraphExecutionMapper executionMapper;

    @Autowired
    private AgentGraphExecutionNodeMapper executionNodeMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ChatModelFactory chatModelFactory;

    @Autowired
    private AgentToolCallbackFactory toolCallbackFactory;

    private final AesGcmCipher cipher = new AesGcmCipher(TEST_CIPHER_KEY);

    // ==================== 建表（V25/V19/V20/V21 H2 脚本 DDL） ====================

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
        jt.execute("""
                CREATE TABLE IF NOT EXISTS sw_agent_tool_internal (
                    id              BIGINT NOT NULL PRIMARY KEY,
                    name            VARCHAR(100) NOT NULL,
                    description     VARCHAR(500) NOT NULL,
                    input_schema    CLOB,
                    bean_name       VARCHAR(100) NOT NULL,
                    method_name     VARCHAR(100) NOT NULL,
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
        jt.execute("""
                CREATE TABLE IF NOT EXISTS sw_agent_tool_external (
                    id              BIGINT NOT NULL PRIMARY KEY,
                    name            VARCHAR(100) NOT NULL,
                    description     VARCHAR(500) NOT NULL,
                    input_schema    CLOB,
                    url             VARCHAR(500) NOT NULL,
                    http_method     VARCHAR(10) NOT NULL DEFAULT 'POST',
                    timeout_seconds INT NOT NULL DEFAULT 30,
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
        jt.execute("CREATE INDEX IF NOT EXISTS idx_sw_agent_model_tenant_deleted ON sw_agent_model_config (tenant_id, deleted)");
        jt.execute("CREATE INDEX IF NOT EXISTS idx_sw_agent_tool_internal_tenant_deleted ON sw_agent_tool_internal (tenant_id, deleted)");
        jt.execute("CREATE INDEX IF NOT EXISTS idx_sw_agent_tool_external_tenant_deleted ON sw_agent_tool_external (tenant_id, deleted)");
        // Step12 执行历史两表（对齐 V27/V28 H2 脚本 DDL）
        jt.execute("""
                CREATE TABLE IF NOT EXISTS sw_agent_graph_execution (
                    id                BIGINT NOT NULL PRIMARY KEY,
                    graph_def_id      BIGINT NOT NULL,
                    graph_def_version INT NOT NULL,
                    status            VARCHAR(20) NOT NULL,
                    input             CLOB,
                    result_text       CLOB,
                    error_category    VARCHAR(50),
                    error_message     CLOB,
                    latency_ms        BIGINT,
                    create_time       TIMESTAMP,
                    create_by         VARCHAR(64),
                    update_time       TIMESTAMP,
                    update_by         VARCHAR(64),
                    deleted           SMALLINT NOT NULL DEFAULT 0,
                    tenant_id         BIGINT NOT NULL DEFAULT 0,
                    version           BIGINT NOT NULL DEFAULT 0
                )
                """);
        jt.execute("CREATE INDEX IF NOT EXISTS idx_sw_agent_gexec_graph ON sw_agent_graph_execution (graph_def_id, deleted)");
        jt.execute("CREATE INDEX IF NOT EXISTS idx_sw_agent_gexec_time ON sw_agent_graph_execution (tenant_id, create_time, deleted)");
        jt.execute("""
                CREATE TABLE IF NOT EXISTS sw_agent_graph_execution_node (
                    id                BIGINT       NOT NULL PRIMARY KEY,
                    execution_id      BIGINT       NOT NULL,
                    node_seq          INT          NOT NULL,
                    branch_id         VARCHAR(64)  NOT NULL,
                    node_id           VARCHAR(100) NOT NULL,
                    node_type         VARCHAR(20)  NOT NULL,
                    node_latency_ms   BIGINT,
                    variable_snapshot CLOB,
                    create_time       TIMESTAMP,
                    create_by         VARCHAR(64),
                    update_time       TIMESTAMP,
                    update_by         VARCHAR(64),
                    deleted           SMALLINT     NOT NULL DEFAULT 0,
                    tenant_id         BIGINT       NOT NULL DEFAULT 0,
                    version           BIGINT       NOT NULL DEFAULT 0
                )
                """);
        jt.execute("CREATE INDEX IF NOT EXISTS idx_sw_agent_genode_exec ON sw_agent_graph_execution_node (execution_id, node_seq, deleted)");
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM sw_agent_graph_execution_node");
        jdbcTemplate.update("DELETE FROM sw_agent_graph_execution");
        jdbcTemplate.update("DELETE FROM sw_agent_graph_def");
        jdbcTemplate.update("DELETE FROM sw_agent_model_config");
        jdbcTemplate.update("DELETE FROM sw_agent_tool_internal");
        jdbcTemplate.update("DELETE FROM sw_agent_tool_external");
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

    // ==================== 用例 1：已发布图执行成功 ====================

    @Test
    @DisplayName("用例1: 执行已发布图（START→LLM→END）→ success=true + 模型输出；latencyMs 非负")
    void execute_publishedLlmGraph_shouldSucceed() {
        Long modelId = insertModelConfig("openai-1", "sk-llm-1");
        when(chatModelFactory.build(any(AgentModelConfig.class), anyString()))
                .thenReturn(new StubChatModel("图执行输出"));
        Long id = createPublishedGraph(llmGraph(modelId));

        AgentGraphExecuteRespDTO resp = service.execute(id, "入参文本");

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getOutput()).isEqualTo("图执行输出");
        assertThat(resp.getErrorMessage()).isNull();
        assertThat(resp.getLatencyMs()).isNotNegative();
    }

    // ==================== 用例 2：DRAFT 图 → PARAM_ERROR ====================

    @Test
    @DisplayName("用例2: 执行 DRAFT 状态的图 → PARAM_ERROR 图未发布")
    void execute_draftGraph_shouldThrowParamError() {
        Long id = graphDefService.create("未发布图");
        graphDefService.saveDraftGraph(id, llmGraph(1L));

        assertThatThrownBy(() -> service.execute(id, "文本"))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("图未发布")
                .satisfies(ex -> assertThat(((BaseException) ex).getCode())
                        .isEqualTo(CommonErrorCode.PARAM_ERROR.getCode()));
    }

    // ==================== 用例 3：不存在 id → NOT_FOUND ====================

    @Test
    @DisplayName("用例3: 执行不存在的图定义 id → NOT_FOUND")
    void execute_unknownId_shouldThrowNotFound() {
        assertThatThrownBy(() -> service.execute(999999L, "文本"))
                .isInstanceOf(BaseException.class)
                .satisfies(ex -> assertThat(((BaseException) ex).getCode())
                        .isEqualTo(CommonErrorCode.NOT_FOUND.getCode()));
    }

    // ==================== 用例 4：LLM 引用不存在模型配置 → 执行前 PARAM_ERROR ====================

    @Test
    @DisplayName("用例4: LLM 节点引用不存在的模型配置 → 执行前 PARAM_ERROR（不做部分执行）")
    void execute_llmRefMissingModelConfig_shouldThrowParamError() {
        Long id = createPublishedGraph(llmGraph(888888L));   // 该 id 无对应配置行

        assertThatThrownBy(() -> service.execute(id, "文本"))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("LLM 节点引用的模型配置不存在")
                .satisfies(ex -> assertThat(((BaseException) ex).getCode())
                        .isEqualTo(CommonErrorCode.PARAM_ERROR.getCode()));
    }

    // ==================== 用例 5：TOOL 引用不存在/未启用工具 → 执行前 PARAM_ERROR ====================

    @Test
    @DisplayName("用例5: TOOL 节点引用不存在或未启用的工具 → 执行前 PARAM_ERROR")
    void execute_toolRefMissingTool_shouldThrowParamError() {
        // 白名单表为空（无 enabled=1 的 echo_tool）
        Long id = createPublishedGraph(toolGraph("echo_tool"));

        assertThatThrownBy(() -> service.execute(id, "文本"))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("工具节点引用的工具不存在或未启用")
                .satisfies(ex -> assertThat(((BaseException) ex).getCode())
                        .isEqualTo(CommonErrorCode.PARAM_ERROR.getCode()));

        // 已存在但 enabled=0（禁用）→ 同样拦截
        insertInternalTool("echo_tool", 0);
        assertThatThrownBy(() -> service.execute(id, "文本"))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("工具节点引用的工具不存在或未启用");
    }

    // ==================== 用例 6：跨租户 → NOT_FOUND ====================

    @Test
    @DisplayName("用例6: 租户 B 执行租户 A 的图 → NOT_FOUND（租户拦截器隔离）")
    void execute_crossTenant_shouldThrowNotFound() {
        Long id = createPublishedGraph(llmGraph(1L));

        setLoginUser(TENANT_200, USER_1);
        assertThatThrownBy(() -> service.execute(id, "文本"))
                .isInstanceOf(BaseException.class)
                .satisfies(ex -> assertThat(((BaseException) ex).getCode())
                        .isEqualTo(CommonErrorCode.NOT_FOUND.getCode()));
    }

    // ==================== 用例 7：START 不唯一 → 执行前 PARAM_ERROR ====================

    @Test
    @DisplayName("用例7: 图中存在 2 个 START 节点 → 执行前 PARAM_ERROR（验收 5 校验项②）")
    void execute_twoStarts_shouldThrowParamError() {
        ProcessGraph graph = graphOf(
                node("node_start_1", "START", Map.of()),
                node("node_start_2", "START", Map.of()),
                node("node_end", "END", Map.of()),
                edge("e1", "node_start_1", "node_end", Map.of()),
                edge("e2", "node_start_2", "node_end", Map.of()));
        Long id = createPublishedGraph(graph);

        assertThatThrownBy(() -> service.execute(id, "文本"))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("START 节点必须唯一")
                .satisfies(ex -> assertThat(((BaseException) ex).getCode())
                        .isEqualTo(CommonErrorCode.PARAM_ERROR.getCode()));
    }

    // ==================== 用例 8：END 不可达 → 执行前 PARAM_ERROR ====================

    @Test
    @DisplayName("用例8: 图中无可达 END 节点 → 执行前 PARAM_ERROR（验收 5 校验项②）")
    void execute_endUnreachable_shouldThrowParamError() {
        // 仅有 START→LLM，无 END 节点
        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_llm", "LLM", Map.of("agentModelConfigId", 1L)),
                edge("e1", "node_start", "node_llm", Map.of()));
        Long id = createPublishedGraph(graph);

        assertThatThrownBy(() -> service.execute(id, "文本"))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("图中不存在可达的 END 节点");
    }

    // ==================== 用例 9：CONDITION 默认边不唯一 → 执行前 PARAM_ERROR ====================

    @Test
    @DisplayName("用例9: CONDITION 出边 ≥2 条无 keyword 边 → 执行前 PARAM_ERROR（验收 5 校验项⑤）")
    void execute_conditionDefaultEdgesNotUnique_shouldThrowParamError() {
        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_cond", "CONDITION", Map.of()),
                node("node_llm_a", "LLM", Map.of("agentModelConfigId", 1L)),
                node("node_llm_b", "LLM", Map.of("agentModelConfigId", 2L)),
                node("node_end", "END", Map.of()),
                edge("e1", "node_start", "node_cond", Map.of()),
                edge("e_d1", "node_cond", "node_llm_a", Map.of()),
                edge("e_d2", "node_cond", "node_llm_b", Map.of()),
                edge("e3", "node_llm_a", "node_end", Map.of()),
                edge("e4", "node_llm_b", "node_end", Map.of()));
        Long id = createPublishedGraph(graph);

        assertThatThrownBy(() -> service.execute(id, "文本"))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("条件分支默认边不唯一");
    }

    // ==================== 用例 10：TOOL 节点执行成功 ====================

    @Test
    @DisplayName("用例10: 执行含 TOOL 节点的已发布图（白名单 enabled=1 + 按名定位回调）→ success=true")
    void execute_toolNode_shouldSucceed() {
        insertInternalTool("echo_tool", 1);
        ToolCallback echo = FunctionToolCallback.builder("echo_tool", (String s) -> "echo:" + s)
                .description("回声工具")
                .inputType(String.class)
                .build();
        when(toolCallbackFactory.buildToolCallbacks(any())).thenReturn(List.of(echo));
        Long id = createPublishedGraph(toolGraph("echo_tool"));

        AgentGraphExecuteRespDTO resp = service.execute(id, "你好");

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getOutput()).isEqualTo("echo:你好");
    }

    // ==================== 用例 11：运行时条件无匹配且无默认边 → success=false ====================

    @Test
    @DisplayName("用例11: CONDITION 无关键词命中且无默认边 → 运行时 success=false + errorMessage（不上抛）")
    void execute_conditionNoMatchNoDefault_shouldReturnFailure() {
        // LLM 分支节点引用的模型配置须通过执行前校验（否则提前 PARAM_ERROR 拦截）
        Long modelId = insertModelConfig("openai-3", "sk-llm-3");
        when(chatModelFactory.build(any(AgentModelConfig.class), anyString()))
                .thenReturn(new StubChatModel("不应到达"));
        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_cond", "CONDITION", Map.of()),
                node("node_llm", "LLM", Map.of("agentModelConfigId", modelId)),
                node("node_end", "END", Map.of()),
                edge("e1", "node_start", "node_cond", Map.of()),
                edge("e_key", "node_cond", "node_llm", Map.of("keyword", "退款")),
                edge("e2", "node_llm", "node_end", Map.of()));
        Long id = createPublishedGraph(graph);

        AgentGraphExecuteRespDTO resp = service.execute(id, "无关文本");

        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.getErrorMessage()).contains("条件分支无匹配且无默认边");
        assertThat(resp.getLatencyMs()).isNotNegative();
    }

    // ==================== 用例 12：模型调用抛异常 → success=false ====================

    @Test
    @DisplayName("用例12: LLM 模型调用抛异常 → success=false + 异常摘要（不抛 500）")
    void execute_llmCallThrows_shouldReturnFailure() {
        Long modelId = insertModelConfig("openai-2", "sk-llm-2");
        when(chatModelFactory.build(any(AgentModelConfig.class), anyString()))
                .thenReturn(new ThrowingChatModel());
        Long id = createPublishedGraph(llmGraph(modelId));

        AgentGraphExecuteRespDTO resp = service.execute(id, "文本");

        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.getErrorMessage()).contains("model exploded");
    }

    // ==================== 用例 13：命名变量存取全链路（Step10 多变量） ====================

    @Test
    @DisplayName("用例13: LLM outputVar 写命名变量 + END inputVar 读回 → success=true + 命名变量值（全链路）")
    void execute_namedVariableGraph_shouldSucceed() {
        Long modelId = insertModelConfig("openai-4", "sk-llm-4");
        when(chatModelFactory.build(any(AgentModelConfig.class), anyString()))
                .thenReturn(new StubChatModel("汇总输出"));
        // LLM 写 summary 变量；END inputVar=summary 取最终输出（默认变量保持入参原值）
        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_llm", "LLM", Map.of("agentModelConfigId", modelId, "outputVar", "summary")),
                node("node_end", "END", Map.of("inputVar", "summary")),
                edge("e1", "node_start", "node_llm", Map.of()),
                edge("e2", "node_llm", "node_end", Map.of()));
        Long id = createPublishedGraph(graph);

        AgentGraphExecuteRespDTO resp = service.execute(id, "入参文本");

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getOutput()).isEqualTo("汇总输出");
        assertThat(resp.getErrorMessage()).isNull();
    }

    // ==================== 用例 14：未定义变量引用 → 运行时 success=false（Step10 多变量） ====================

    @Test
    @DisplayName("用例14: LLM inputVar 引用未定义变量 → 运行时 success=false + errorMessage（不上抛）")
    void execute_undefinedVariable_shouldReturnFailure() {
        Long modelId = insertModelConfig("openai-5", "sk-llm-5");
        when(chatModelFactory.build(any(AgentModelConfig.class), anyString()))
                .thenReturn(new StubChatModel("不应到达"));
        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_llm", "LLM", Map.of("agentModelConfigId", modelId, "inputVar", "missing")),
                node("node_end", "END", Map.of()),
                edge("e1", "node_start", "node_llm", Map.of()),
                edge("e2", "node_llm", "node_end", Map.of()));
        Long id = createPublishedGraph(graph);

        AgentGraphExecuteRespDTO resp = service.execute(id, "文本");

        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.getErrorMessage()).contains("引用了未定义的变量: missing");
        assertThat(resp.getLatencyMs()).isNotNegative();
    }

    // ==================== 用例 15：全链路循环图（Step11 LOOP） ====================

    @Test
    @DisplayName("用例15: 全链路循环图（LOOP→LLM→CONDITION 回边，graph_json 序列化往返）→ success=true + 循环退出输出")
    void execute_loopGraph_shouldSucceed() {
        Long modelId = insertModelConfig("openai-6", "sk-loop");
        // 三次 build 依次返回：前两轮无退出关键词 → 默认边回 LOOP；第三轮"退出" → 走 END
        when(chatModelFactory.build(any(AgentModelConfig.class), anyString()))
                .thenReturn(new StubChatModel("结果1"), new StubChatModel("结果2"), new StubChatModel("退出"));
        // 与解释器单测同构的循环图（经 graph_json 序列化往返，验证不透明 config 键 maxIterations 零迁移）
        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_loop", "LOOP", Map.of("maxIterations", 3)),
                node("node_llm", "LLM", Map.of("agentModelConfigId", modelId)),
                node("node_cond", "CONDITION", Map.of()),
                node("node_end", "END", Map.of()),
                edge("e1", "node_start", "node_loop", Map.of()),
                edge("e2", "node_loop", "node_llm", Map.of()),
                edge("e3", "node_llm", "node_cond", Map.of()),
                edge("e_exit", "node_cond", "node_end", Map.of("keyword", "退出")),
                edge("e_back", "node_cond", "node_loop", Map.of()));
        Long id = createPublishedGraph(graph);

        AgentGraphExecuteRespDTO resp = service.execute(id, "开始");

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getOutput()).isEqualTo("退出");
        assertThat(resp.getErrorMessage()).isNull();
    }

    // ==================== 用例 16：全链路并行图（Step11 FORK→JOIN） ====================

    @Test
    @DisplayName("用例16: 全链路并行图（FORK→JOIN 两分支全执行并汇合）→ success=true + 汇合输出")
    void execute_forkJoinGraph_shouldSucceed() {
        Long modelId = insertModelConfig("openai-7", "sk-b1");
        insertInternalTool("echo_tool", 1);
        when(chatModelFactory.build(any(AgentModelConfig.class), anyString()))
                .thenReturn(new StubChatModel("分支1输出"));
        ToolCallback echo = FunctionToolCallback.builder("echo_tool", (String s) -> "分支2输出")
                .description("回声工具")
                .inputType(String.class)
                .build();
        when(toolCallbackFactory.buildToolCallbacks(any())).thenReturn(List.of(echo));

        // B1: LLM 写 v1；B2: TOOL 写 v2；JOIN 汇合后 END 从 v1 取最终输出
        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_fork", "FORK", Map.of()),
                node("node_llm_b1", "LLM", Map.of("agentModelConfigId", modelId, "outputVar", "v1")),
                node("node_tool_b2", "TOOL", Map.of("toolName", "echo_tool", "outputVar", "v2")),
                node("node_join", "JOIN", Map.of()),
                node("node_end", "END", Map.of("inputVar", "v1")),
                edge("e1", "node_start", "node_fork", Map.of()),
                edge("e2", "node_fork", "node_llm_b1", Map.of()),
                edge("e3", "node_fork", "node_tool_b2", Map.of()),
                edge("e4", "node_llm_b1", "node_join", Map.of()),
                edge("e5", "node_tool_b2", "node_join", Map.of()),
                edge("e6", "node_join", "node_end", Map.of()));
        Long id = createPublishedGraph(graph);

        AgentGraphExecuteRespDTO resp = service.execute(id, "入参文本");

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getOutput()).isEqualTo("分支1输出");
        assertThat(resp.getErrorMessage()).isNull();
    }

    // ==================== 用例 17：FORK 出边 <2 → 执行前 PARAM_ERROR ====================

    @Test
    @DisplayName("用例17: FORK 节点仅 1 条出边 → 执行前 PARAM_ERROR 扇出分支数必须 ≥ 2")
    void execute_forkWithSingleOutEdge_shouldThrowParamError() {
        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_fork", "FORK", Map.of()),
                node("node_end", "END", Map.of()),
                edge("e1", "node_start", "node_fork", Map.of()),
                edge("e2", "node_fork", "node_end", Map.of()));
        Long id = createPublishedGraph(graph);

        assertThatThrownBy(() -> service.execute(id, "文本"))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("FORK 节点扇出分支数必须 ≥ 2")
                .satisfies(ex -> assertThat(((BaseException) ex).getCode())
                        .isEqualTo(CommonErrorCode.PARAM_ERROR.getCode()));
    }

    // ==================== 用例 18：JOIN 入边 <2 → 执行前 PARAM_ERROR ====================

    @Test
    @DisplayName("用例18: JOIN 节点仅 1 条入边 → 执行前 PARAM_ERROR 汇合入边数必须 ≥ 2")
    void execute_joinWithSingleInEdge_shouldThrowParamError() {
        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_join", "JOIN", Map.of()),
                node("node_end", "END", Map.of()),
                edge("e1", "node_start", "node_join", Map.of()),
                edge("e2", "node_join", "node_end", Map.of()));
        Long id = createPublishedGraph(graph);

        assertThatThrownBy(() -> service.execute(id, "文本"))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("JOIN 节点汇合入边数必须 ≥ 2")
                .satisfies(ex -> assertThat(((BaseException) ex).getCode())
                        .isEqualTo(CommonErrorCode.PARAM_ERROR.getCode()));
    }

    // ==================== 用例 19：LOOP maxIterations <1 → 执行前 PARAM_ERROR ====================

    @Test
    @DisplayName("用例19: LOOP maxIterations=0 → 执行前 PARAM_ERROR maxIterations 必须 ≥ 1")
    void execute_loopWithZeroMaxIterations_shouldThrowParamError() {
        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_loop", "LOOP", Map.of("maxIterations", 0)),
                node("node_end", "END", Map.of()),
                edge("e1", "node_start", "node_loop", Map.of()),
                edge("e2", "node_loop", "node_end", Map.of()));
        Long id = createPublishedGraph(graph);

        assertThatThrownBy(() -> service.execute(id, "文本"))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("LOOP 节点 maxIterations 必须 ≥ 1")
                .satisfies(ex -> assertThat(((BaseException) ex).getCode())
                        .isEqualTo(CommonErrorCode.PARAM_ERROR.getCode()));
    }

    // ==================== 用例 20：执行成功 → 全链路落库（Step12） ====================

    @Test
    @DisplayName("用例20: 执行成功 → 落库 status=SUCCESS + output/input/graphDefId/version + 节点明细 3 行（START/LLM/END）+ 响应 executionId")
    void execute_success_shouldPersistHistory() {
        Long modelId = insertModelConfig("openai-8", "sk-persist");
        when(chatModelFactory.build(any(AgentModelConfig.class), anyString()))
                .thenReturn(new StubChatModel("持久化输出"));
        Long id = createPublishedGraph(llmGraph(modelId));

        AgentGraphExecuteRespDTO resp = service.execute(id, "入参文本");

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getExecutionId()).isNotNull();
        AgentGraphExecution exec = executionMapper.selectById(resp.getExecutionId());
        assertThat(exec).isNotNull();
        assertThat(exec.getStatus()).isEqualTo("SUCCESS");
        assertThat(exec.getResultText()).isEqualTo("持久化输出");
        assertThat(exec.getInput()).isEqualTo("入参文本");
        assertThat(exec.getGraphDefId()).isEqualTo(id);
        assertThat(exec.getGraphDefVersion()).isEqualTo(2);
        assertThat(exec.getErrorCategory()).isNull();
        assertThat(exec.getLatencyMs()).isNotNegative();
        // 节点明细 3 行：START/LLM/END，branchId 全 0，快照含默认变量
        List<AgentGraphExecutionNode> nodes = executionNodeMapper.selectList(
                Wrappers.<AgentGraphExecutionNode>lambdaQuery()
                        .eq(AgentGraphExecutionNode::getExecutionId, exec.getId())
                        .orderByAsc(AgentGraphExecutionNode::getNodeSeq));
        assertThat(nodes).hasSize(3);
        assertThat(nodes.get(0).getNodeType()).isEqualTo("START");
        assertThat(nodes.get(1).getNodeType()).isEqualTo("LLM");
        assertThat(nodes.get(2).getNodeType()).isEqualTo("END");
        assertThat(nodes).allSatisfy(n -> {
            assertThat(n.getBranchId()).isEqualTo("0");
            assertThat(n.getNodeLatencyMs()).isNotNegative();
            assertThat(n.getVariableSnapshot()).contains("input");
        });
    }

    // ==================== 用例 21：运行时失败 → 失败路径落库（Step12） ====================

    @Test
    @DisplayName("用例21: 条件无匹配且无默认边 → 落库 status=FAILED + errorCategory=CONDITION_NO_MATCH + 节点明细含失败节点行")
    void execute_runtimeFailure_shouldPersistFailure() {
        Long modelId = insertModelConfig("openai-9", "sk-fail");
        when(chatModelFactory.build(any(AgentModelConfig.class), anyString()))
                .thenReturn(new StubChatModel("不应到达"));
        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_cond", "CONDITION", Map.of()),
                node("node_llm", "LLM", Map.of("agentModelConfigId", modelId)),
                node("node_end", "END", Map.of()),
                edge("e1", "node_start", "node_cond", Map.of()),
                edge("e_key", "node_cond", "node_llm", Map.of("keyword", "退款")),
                edge("e2", "node_llm", "node_end", Map.of()));
        Long id = createPublishedGraph(graph);

        AgentGraphExecuteRespDTO resp = service.execute(id, "无关文本");

        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.getExecutionId()).isNotNull();
        AgentGraphExecution exec = executionMapper.selectById(resp.getExecutionId());
        assertThat(exec.getStatus()).isEqualTo("FAILED");
        assertThat(exec.getErrorCategory()).isEqualTo("CONDITION_NO_MATCH");
        assertThat(exec.getErrorMessage()).contains("条件分支无匹配且无默认边");
        assertThat(exec.getResultText()).isNull();
        // 失败路径节点明细完整：START + 失败节点 CONDITION（各占一行）
        List<AgentGraphExecutionNode> nodes = executionNodeMapper.selectList(
                Wrappers.<AgentGraphExecutionNode>lambdaQuery()
                        .eq(AgentGraphExecutionNode::getExecutionId, exec.getId())
                        .orderByAsc(AgentGraphExecutionNode::getNodeSeq));
        assertThat(nodes).hasSize(2);
        assertThat(nodes.get(0).getNodeId()).isEqualTo("node_start");
        assertThat(nodes.get(1).getNodeId()).isEqualTo("node_cond");
        assertThat(nodes.get(1).getNodeLatencyMs()).isNotNegative();
    }

    // ==================== 用例 22-26：错误分类维度落库（Step12 §5.3） ====================

    @Test
    @DisplayName("用例22: LLM 第三方异常 → 落库 errorCategory=MODEL_CALL_FAILED")
    void execute_llmThirdPartyFailure_shouldPersistModelCallFailed() {
        Long modelId = insertModelConfig("openai-10", "sk-throw");
        when(chatModelFactory.build(any(AgentModelConfig.class), anyString()))
                .thenReturn(new ThrowingChatModel());
        Long id = createPublishedGraph(llmGraph(modelId));

        AgentGraphExecuteRespDTO resp = service.execute(id, "文本");

        assertThat(resp.isSuccess()).isFalse();
        AgentGraphExecution exec = executionMapper.selectById(resp.getExecutionId());
        assertThat(exec.getErrorCategory()).isEqualTo("MODEL_CALL_FAILED");
        assertThat(exec.getErrorMessage()).contains("model exploded");
    }

    @Test
    @DisplayName("用例23: 步数超限（预算耗尽仍死循环）→ 落库 errorCategory=STEP_LIMIT")
    void execute_stepLimit_shouldPersistStepLimit() {
        Long modelId = insertModelConfig("openai-11", "sk-loop-limit");
        when(chatModelFactory.build(any(AgentModelConfig.class), anyString()))
                .thenReturn(new StubChatModel("死循环"));
        // START→CONDITION（keyword 边→END / 默认边→LLM）→LLM→CONDITION：输入与 LLM
        // 输出永不含关键词 → 死循环，靠全局步数兜底（END 可达，执行前校验放行）
        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_cond", "CONDITION", Map.of()),
                node("node_llm", "LLM", Map.of("agentModelConfigId", modelId)),
                node("node_end", "END", Map.of()),
                edge("e1", "node_start", "node_cond", Map.of()),
                edge("e_exit", "node_cond", "node_end", Map.of("keyword", "exit")),
                edge("e_back", "node_cond", "node_llm", Map.of()),
                edge("e4", "node_llm", "node_cond", Map.of()));
        Long id = createPublishedGraph(graph);

        AgentGraphExecuteRespDTO resp = service.execute(id, "无关键词文本");

        assertThat(resp.isSuccess()).isFalse();
        AgentGraphExecution exec = executionMapper.selectById(resp.getExecutionId());
        assertThat(exec.getErrorCategory()).isEqualTo("STEP_LIMIT");
        assertThat(exec.getErrorMessage()).contains("执行步数超限");
    }

    @Test
    @DisplayName("用例24: 循环迭代超限 → 落库 errorCategory=LOOP_LIMIT")
    void execute_loopLimit_shouldPersistLoopLimit() {
        Long modelId = insertModelConfig("openai-12", "sk-loop-over");
        when(chatModelFactory.build(any(AgentModelConfig.class), anyString()))
                .thenReturn(new StubChatModel("永不退出"));
        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_loop", "LOOP", Map.of("maxIterations", 1)),
                node("node_llm", "LLM", Map.of("agentModelConfigId", modelId)),
                node("node_cond", "CONDITION", Map.of()),
                node("node_end", "END", Map.of()),
                edge("e1", "node_start", "node_loop", Map.of()),
                edge("e2", "node_loop", "node_llm", Map.of()),
                edge("e3", "node_llm", "node_cond", Map.of()),
                edge("e_exit", "node_cond", "node_end", Map.of("keyword", "exit")),
                edge("e_back", "node_cond", "node_loop", Map.of()));
        Long id = createPublishedGraph(graph);

        AgentGraphExecuteRespDTO resp = service.execute(id, "开始");

        assertThat(resp.isSuccess()).isFalse();
        AgentGraphExecution exec = executionMapper.selectById(resp.getExecutionId());
        assertThat(exec.getErrorCategory()).isEqualTo("LOOP_LIMIT");
        assertThat(exec.getErrorMessage()).contains("循环迭代次数超限");
    }

    @Test
    @DisplayName("用例25: 未定义变量引用 → 落库 errorCategory=UNDEFINED_VARIABLE")
    void execute_undefinedVariable_shouldPersistUndefinedVariable() {
        Long modelId = insertModelConfig("openai-13", "sk-undef");
        when(chatModelFactory.build(any(AgentModelConfig.class), anyString()))
                .thenReturn(new StubChatModel("不应到达"));
        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_llm", "LLM", Map.of("agentModelConfigId", modelId, "inputVar", "missing")),
                node("node_end", "END", Map.of()),
                edge("e1", "node_start", "node_llm", Map.of()),
                edge("e2", "node_llm", "node_end", Map.of()));
        Long id = createPublishedGraph(graph);

        AgentGraphExecuteRespDTO resp = service.execute(id, "文本");

        assertThat(resp.isSuccess()).isFalse();
        AgentGraphExecution exec = executionMapper.selectById(resp.getExecutionId());
        assertThat(exec.getErrorCategory()).isEqualTo("UNDEFINED_VARIABLE");
    }

    @Test
    @DisplayName("用例26: 工具回调运行时异常 → 落库 errorCategory=TOOL_CALL_FAILED")
    void execute_toolRuntimeFailure_shouldPersistToolCallFailed() {
        insertInternalTool("boom_tool", 1);
        ToolCallback boom = FunctionToolCallback.builder("boom_tool", (String s) -> {
            throw new IllegalStateException("tool exploded");
        })
                .description("抛错工具")
                .inputType(String.class)
                .build();
        when(toolCallbackFactory.buildToolCallbacks(any())).thenReturn(List.of(boom));
        Long id = createPublishedGraph(toolGraph("boom_tool"));

        AgentGraphExecuteRespDTO resp = service.execute(id, "你好");

        assertThat(resp.isSuccess()).isFalse();
        AgentGraphExecution exec = executionMapper.selectById(resp.getExecutionId());
        assertThat(exec.getErrorCategory()).isEqualTo("TOOL_CALL_FAILED");
        assertThat(exec.getErrorMessage()).contains("tool exploded");
    }

    // ==================== 用例 27：执行历史列表（Step12 查询端点） ====================

    @Test
    @DisplayName("用例27: 执行历史列表 — 分页 + graphDefId 过滤；无过滤 = 全部")
    void pageExecutions_shouldReturnPagedAndFiltered() {
        Long modelId = insertModelConfig("openai-14", "sk-list");
        when(chatModelFactory.build(any(AgentModelConfig.class), anyString()))
                .thenReturn(new StubChatModel("列表输出"));
        Long id1 = createPublishedGraph(llmGraph(modelId));
        Long id2 = createPublishedGraph(llmGraph(modelId));

        service.execute(id1, "第一次");
        service.execute(id2, "第二次");
        service.execute(id1, "第三次");

        PageResult<AgentGraphExecutionDTO> byGraph = service.pageExecutions(new PageParam(), id1);
        assertThat(byGraph.getTotal()).isEqualTo(2);
        assertThat(byGraph.getRecords()).hasSize(2);
        assertThat(byGraph.getRecords()).allSatisfy(r -> {
            assertThat(r.getStatus()).isEqualTo("SUCCESS");
            assertThat(r.getGraphDefId()).isEqualTo(id1);
            assertThat(r.getLatencyMs()).isNotNegative();
        });
        PageResult<AgentGraphExecutionDTO> all = service.pageExecutions(new PageParam(), null);
        assertThat(all.getTotal()).isEqualTo(3);
        assertThat(all.getRecords()).hasSize(3);
    }

    // ==================== 用例 28：执行详情（Step12 查询端点） ====================

    @Test
    @DisplayName("用例28: 执行详情 — input/output/latency/version 回显；不存在的 id → NOT_FOUND")
    void getExecution_shouldReturnDetailOrNotFound() {
        Long modelId = insertModelConfig("openai-15", "sk-detail");
        when(chatModelFactory.build(any(AgentModelConfig.class), anyString()))
                .thenReturn(new StubChatModel("详情输出"));
        Long id = createPublishedGraph(llmGraph(modelId));

        AgentGraphExecuteRespDTO resp = service.execute(id, "详情入参");

        AgentGraphExecutionDetailDTO detail = service.getExecution(resp.getExecutionId());
        assertThat(detail.getId()).isEqualTo(resp.getExecutionId());
        assertThat(detail.getStatus()).isEqualTo("SUCCESS");
        assertThat(detail.getInput()).isEqualTo("详情入参");
        assertThat(detail.getOutput()).isEqualTo("详情输出");
        assertThat(detail.getLatencyMs()).isNotNegative();
        assertThat(detail.getGraphDefVersion()).isEqualTo(2);
        assertThat(detail.getCreateTime()).isNotNull();

        assertThatThrownBy(() -> service.getExecution(999999L))
                .isInstanceOf(BaseException.class)
                .satisfies(ex -> assertThat(((BaseException) ex).getCode())
                        .isEqualTo(CommonErrorCode.NOT_FOUND.getCode()));
    }

    // ==================== 用例 29：节点明细（Step12 查询端点） ====================

    @Test
    @DisplayName("用例29: 节点明细 — nodeSeq 升序返回；执行记录不存在 → NOT_FOUND")
    void listExecutionNodes_shouldReturnOrderedOrNotFound() {
        Long modelId = insertModelConfig("openai-16", "sk-nodes");
        when(chatModelFactory.build(any(AgentModelConfig.class), anyString()))
                .thenReturn(new StubChatModel("节点输出"));
        Long id = createPublishedGraph(llmGraph(modelId));

        AgentGraphExecuteRespDTO resp = service.execute(id, "入参");

        List<AgentGraphExecutionNodeDTO> nodes = service.listExecutionNodes(resp.getExecutionId());
        assertThat(nodes).hasSize(3);
        assertThat(nodes.get(0).getNodeSeq()).isEqualTo(1);
        assertThat(nodes.get(0).getNodeType()).isEqualTo("START");
        assertThat(nodes.get(1).getNodeSeq()).isEqualTo(2);
        assertThat(nodes.get(1).getNodeType()).isEqualTo("LLM");
        assertThat(nodes.get(2).getNodeSeq()).isEqualTo(3);
        assertThat(nodes.get(2).getNodeType()).isEqualTo("END");
        assertThat(nodes.get(1).getVariableSnapshot()).contains("input");

        assertThatThrownBy(() -> service.listExecutionNodes(999999L))
                .isInstanceOf(BaseException.class)
                .satisfies(ex -> assertThat(((BaseException) ex).getCode())
                        .isEqualTo(CommonErrorCode.NOT_FOUND.getCode()));
    }

    // ==================== 用例 30：跨租户隔离（Step12 查询） ====================

    @Test
    @DisplayName("用例30: 跨租户隔离 — 租户 B 查执行历史列表为空、详情/节点 → NOT_FOUND")
    void executionHistory_crossTenant_shouldBeIsolated() {
        Long modelId = insertModelConfig("openai-17", "sk-tenant");
        when(chatModelFactory.build(any(AgentModelConfig.class), anyString()))
                .thenReturn(new StubChatModel("租户输出"));
        Long id = createPublishedGraph(llmGraph(modelId));
        AgentGraphExecuteRespDTO resp = service.execute(id, "入参");

        setLoginUser(TENANT_200, USER_1);
        assertThat(service.pageExecutions(new PageParam(), null).getTotal()).isZero();
        assertThatThrownBy(() -> service.getExecution(resp.getExecutionId()))
                .isInstanceOf(BaseException.class)
                .satisfies(ex -> assertThat(((BaseException) ex).getCode())
                        .isEqualTo(CommonErrorCode.NOT_FOUND.getCode()));
        assertThatThrownBy(() -> service.listExecutionNodes(resp.getExecutionId()))
                .isInstanceOf(BaseException.class)
                .satisfies(ex -> assertThat(((BaseException) ex).getCode())
                        .isEqualTo(CommonErrorCode.NOT_FOUND.getCode()));
    }

    // ==================== 内部辅助 ====================

    /** 创建 → 覆盖图 → 发布，返回已发布图 id */
    private Long createPublishedGraph(ProcessGraph graph) {
        Long id = graphDefService.create(graph.getName());
        graphDefService.saveDraftGraph(id, graph);
        graphDefService.publish(id);
        return id;
    }

    /** 标准 LLM 图：START→LLM(modelConfigId)→END */
    private ProcessGraph llmGraph(Long modelConfigId) {
        return graphOf(
                node("node_start", "START", Map.of()),
                node("node_llm", "LLM", Map.of("agentModelConfigId", modelConfigId)),
                node("node_end", "END", Map.of()),
                edge("e1", "node_start", "node_llm", Map.of()),
                edge("e2", "node_llm", "node_end", Map.of()));
    }

    /** 标准 TOOL 图：START→TOOL(toolName)→END */
    private ProcessGraph toolGraph(String toolName) {
        return graphOf(
                node("node_start", "START", Map.of()),
                node("node_tool", "TOOL", Map.of("toolName", toolName)),
                node("node_end", "END", Map.of()),
                edge("e1", "node_start", "node_tool", Map.of()),
                edge("e2", "node_tool", "node_end", Map.of()));
    }

    private ProcessGraph graphOf(GraphElement... elements) {
        ProcessGraph graph = new ProcessGraph();
        graph.setGraphKey("exec_test_key");
        graph.setName("执行测试图");
        graph.setVersion(1);
        graph.setElements(Arrays.asList(elements));
        graph.setCanvas(Map.of());
        return graph;
    }

    private GraphElement node(String id, String type, Map<String, Object> config) {
        return GraphElement.builder()
                .id(id).kind("node").type(type)
                .config(config).style(Map.of())
                .build();
    }

    private GraphElement edge(String id, String source, String target, Map<String, Object> config) {
        return GraphElement.builder()
                .id(id).kind("edge").source(source).target(target)
                .config(config).style(Map.of())
                .build();
    }

    /** 插入租户内模型配置行（返回 id） */
    private Long insertModelConfig(String name, String plainKey) {
        AgentModelConfig config = new AgentModelConfig();
        config.setName(name);
        config.setProtocolType("openai");
        config.setBaseUrl("http://localhost:9999/v1");
        config.setModelName("stub-model");
        config.setApiKeyCipher(cipher.encrypt(plainKey));
        config.setEnabled(true);
        modelConfigMapper.insert(config);
        return config.getId();
    }

    /** 插入内部工具白名单行（enabled=1 启用 / 0 禁用） */
    private void insertInternalTool(String toolName, int enabled) {
        AgentToolInternalConfig tool = new AgentToolInternalConfig();
        tool.setName(toolName);
        tool.setDescription("测试工具");
        tool.setBeanName("echoBean");
        tool.setMethodName("execute");
        tool.setEnabled(enabled == 1);
        internalToolMapper.insert(tool);
    }

    // ==================== ChatModel 桩 ====================

    /** 固定回复的 ChatModel 桩 */
    static class StubChatModel implements ChatModel {
        private final String reply;

        StubChatModel(String reply) {
            this.reply = reply;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
        }
    }

    /** 调用即抛异常的 ChatModel 桩 */
    static class ThrowingChatModel implements ChatModel {
        @Override
        public ChatResponse call(Prompt prompt) {
            throw new IllegalStateException("model exploded");
        }
    }

    // ==================== 组合测试配置 ====================

    @Configuration
    @MapperScan("com.sw.ck.agent.mapper")
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        public DataSource dataSource() {
            return DataSourceBuilder.create()
                    .url("jdbc:h2:mem:agentgraphexec;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
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
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        // ==================== 业务 Bean ====================

        @Bean
        public AgentGraphDefService agentGraphDefService(ObjectMapper objectMapper) {
            return new AgentGraphDefServiceImpl(objectMapper);
        }

        @Bean
        public ChatModelFactory chatModelFactory() {
            return mock(ChatModelFactory.class);
        }

        @Bean
        public AgentToolCallbackFactory agentToolCallbackFactory() {
            return mock(AgentToolCallbackFactory.class);
        }

        @Bean
        public AesGcmCipher aesGcmCipher() {
            return new AesGcmCipher(TEST_CIPHER_KEY);
        }

        @Bean
        public AgentGraphExecutionService agentGraphExecutionService(
                ObjectMapper objectMapper,
                AgentModelConfigMapper modelConfigMapper,
                AgentToolInternalConfigMapper internalToolMapper,
                AgentToolExternalConfigMapper externalToolMapper,
                AgentGraphExecutionMapper executionMapper,
                AgentGraphExecutionNodeMapper executionNodeMapper,
                ChatModelFactory chatModelFactory,
                AesGcmCipher aesGcmCipher,
                LoginContextProvider loginContextProvider) {
            return new AgentGraphExecutionServiceImpl(objectMapper, modelConfigMapper,
                    internalToolMapper, externalToolMapper, executionMapper,
                    executionNodeMapper, chatModelFactory, aesGcmCipher,
                    loginContextProvider);
        }
    }
}
