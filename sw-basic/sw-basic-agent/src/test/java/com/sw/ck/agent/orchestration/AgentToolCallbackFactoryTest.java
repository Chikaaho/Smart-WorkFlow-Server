package com.sw.ck.agent.orchestration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sw.ck.agent.entity.tool.AgentToolExternalConfig;
import com.sw.ck.agent.entity.tool.AgentToolInternalConfig;
import com.sw.ck.agent.mapper.tool.AgentToolExternalConfigMapper;
import com.sw.ck.agent.mapper.tool.AgentToolInternalConfigMapper;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AgentToolCallbackFactory} 测试（M07 Step3 §10，纯 JUnit，不启动 Spring 上下文）。
 * <p>
 * 策略：mock 两个 Mapper（{@code selectList} 返回配置列表）+ mock {@link ApplicationContext}
 * （{@code getBean} 返回白名单 bean 或抛 {@link NoSuchBeanDefinitionException}）。
 * 外部工具调用用 JDK 内置 {@code com.sun.net.httpserver.HttpServer}（localhost 随机端口，
 * 同 Step2 先例），超时行为用"服务端延迟响应 > 配置 readTimeout"行为验证。
 * </p>
 * <p>
 * 入参约定（回执 §3.4 实测）：{@code inputType(String.class)} 时 tool_calls 的 arguments
 * 必须是 JSON 字符串字面量，lambda 收到原始参数字符串——测试中调用
 * {@code cb.call("\"{\\\"a\\\":1}\"")}（内层双引号为 JSON 字符串字面量语法）。
 * </p>
 */
@DisplayName("工具回调工厂测试")
class AgentToolCallbackFactoryTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    // ==================== 用例 1：内部工具构造 + 调用链路 ====================

    @Test
    @DisplayName("用例1: 内部工具 FunctionToolCallback 构造正确（name/description/inputSchema 传入；inputSchema=null 回退生成 schema），call 链路到达白名单 bean 方法")
    void internalTool_shouldBuildCallbackWithDefinitionAndInvokeBean() {
        AgentToolInternalConfig config = internalConfig("sum_tool", "求和工具",
                "{\"type\":\"string\"}", "echoToolBean", "execute");
        // inputSchema=null 场景（回执 §3.4b：不 NPE，回退由 inputType 生成 {"type":"string"}）
        AgentToolInternalConfig nullSchema = internalConfig("null_schema_tool", "无 schema 工具",
                null, "echoToolBean", "execute");

        AgentToolInternalConfigMapper internalMapper = mock(AgentToolInternalConfigMapper.class);
        when(internalMapper.selectList(any())).thenReturn(List.of(config, nullSchema));
        AgentToolExternalConfigMapper externalMapper = mock(AgentToolExternalConfigMapper.class);
        when(externalMapper.selectList(any())).thenReturn(List.of());
        ApplicationContext ctx = mock(ApplicationContext.class);
        EchoToolBean bean = new EchoToolBean();
        when(ctx.getBean("echoToolBean")).thenReturn(bean);

        AgentToolCallbackFactory factory = new AgentToolCallbackFactory(internalMapper, externalMapper, ctx);
        List<ToolCallback> callbacks = factory.buildToolCallbacks(100L);

        assertThat(callbacks).hasSize(2);
        ToolCallback cb = callbacks.get(0);
        assertThat(cb.getToolDefinition().name()).isEqualTo("sum_tool");
        assertThat(cb.getToolDefinition().description()).isEqualTo("求和工具");
        assertThat(cb.getToolDefinition().inputSchema()).isEqualTo("{\"type\":\"string\"}");
        // call 链路：JSON 字符串字面量入参 → 反序列化为原始字符串 → 反射调用 bean.execute(String)
        String result = cb.call("\"{\\\"a\\\":1}\"");
        assertThat(result).contains("echo:");

        ToolCallback nullSchemaCb = callbacks.get(1);
        assertThat(nullSchemaCb.getToolDefinition().inputSchema())
                .as("inputSchema=null 时不 NPE，回退由 inputType 生成 string schema")
                .contains("string");
    }

    // ==================== 用例 2：外部工具构造 + 真实 HTTP 调用 ====================

    @Test
    @DisplayName("用例2: 外部工具 FunctionToolCallback 构造正确，call 发起 POST 到白名单 URL 并返回响应体")
    void externalTool_shouldBuildCallbackAndCallMockServer() throws Exception {
        server = startServer((exchange, body) -> {
            String respBody = "pong:" + body;
            byte[] out = respBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        int port = server.getAddress().getPort();
        AgentToolExternalConfig config = externalConfig("weather_tool", "天气查询工具",
                "{\"type\":\"object\"}", "http://127.0.0.1:" + port + "/weather", "POST", 5);

        AgentToolInternalConfigMapper internalMapper = mock(AgentToolInternalConfigMapper.class);
        when(internalMapper.selectList(any())).thenReturn(List.of());
        AgentToolExternalConfigMapper externalMapper = mock(AgentToolExternalConfigMapper.class);
        when(externalMapper.selectList(any())).thenReturn(List.of(config));

        AgentToolCallbackFactory factory = new AgentToolCallbackFactory(
                internalMapper, externalMapper, mock(ApplicationContext.class));
        List<ToolCallback> callbacks = factory.buildToolCallbacks(null);

        assertThat(callbacks).hasSize(1);
        ToolCallback cb = callbacks.get(0);
        assertThat(cb.getToolDefinition().name()).isEqualTo("weather_tool");
        assertThat(cb.getToolDefinition().description()).isEqualTo("天气查询工具");
        assertThat(cb.getToolDefinition().inputSchema()).isEqualTo("{\"type\":\"object\"}");

        String result = cb.call("\"{\\\"city\\\":\\\"beijing\\\"}\"");
        assertThat(result).contains("pong:").contains("city");
    }

    // ==================== 用例 3：外部工具超时从 DB 配置读 ====================

    @Test
    @DisplayName("用例3: timeoutSeconds 从配置读取（服务端延迟 3s > readTimeout 1s → 调用抛 SocketTimeoutException）")
    void externalTool_timeoutFromConfig_shouldApplyReadTimeout() throws Exception {
        server = startServer((exchange, body) -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] out = "late".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        int port = server.getAddress().getPort();
        AgentToolExternalConfig config = externalConfig("slow_tool", "慢服务",
                null, "http://127.0.0.1:" + port + "/slow", "POST", 1);

        AgentToolInternalConfigMapper internalMapper = mock(AgentToolInternalConfigMapper.class);
        when(internalMapper.selectList(any())).thenReturn(List.of());
        AgentToolExternalConfigMapper externalMapper = mock(AgentToolExternalConfigMapper.class);
        when(externalMapper.selectList(any())).thenReturn(List.of(config));

        AgentToolCallbackFactory factory = new AgentToolCallbackFactory(
                internalMapper, externalMapper, mock(ApplicationContext.class));
        ToolCallback cb = factory.buildToolCallbacks(null).get(0);

        assertThatThrownBy(() -> cb.call("\"ping\""))
                .as("readTimeout=1s 时慢服务应触发读超时（超时值来自 DB timeout_seconds）")
                .isInstanceOf(RuntimeException.class)
                .hasRootCauseInstanceOf(SocketTimeoutException.class);
    }

    // ==================== 用例 4：fail-fast（bean 不存在 / 方法不存在 / 非法 HTTP 方法） ====================

    @Test
    @DisplayName("用例4: beanName 不在容器 → NoSuchBeanDefinitionException；methodName 不存在 → IllegalStateException；非法 HTTP 方法 → IllegalArgumentException（构造阶段抛出）")
    void invalidConfig_shouldFailFast() {
        // 4a. beanName 不存在
        AgentToolInternalConfig ghostBean = internalConfig("ghost_tool", "幽灵 bean",
                null, "ghostBean", "execute");
        AgentToolInternalConfigMapper internalMapper = mock(AgentToolInternalConfigMapper.class);
        when(internalMapper.selectList(any())).thenReturn(List.of(ghostBean));
        AgentToolExternalConfigMapper externalMapper = mock(AgentToolExternalConfigMapper.class);
        when(externalMapper.selectList(any())).thenReturn(List.of());
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBean("ghostBean")).thenThrow(new NoSuchBeanDefinitionException("ghostBean"));

        AgentToolCallbackFactory factory = new AgentToolCallbackFactory(internalMapper, externalMapper, ctx);
        assertThatThrownBy(() -> factory.buildToolCallbacks(null))
                .as("beanName 不在 ApplicationContext 时应于工厂构造阶段 fail-fast")
                .isInstanceOf(NoSuchBeanDefinitionException.class);

        // 4b. methodName 不存在（bean 无 execute(String) 方法）
        AgentToolInternalConfig noMethod = internalConfig("nomethod_tool", "无方法",
                null, "plainObjectBean", "execute");
        when(internalMapper.selectList(any())).thenReturn(List.of(noMethod));
        when(ctx.getBean("plainObjectBean")).thenReturn(new Object());
        assertThatThrownBy(() -> factory.buildToolCallbacks(null))
                .as("methodName 不存在时应于工厂构造阶段 fail-fast")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("execute");

        // 4c. 外部工具非法 HTTP 方法
        AgentToolExternalConfig badMethod = externalConfig("bad_tool", "非法方法",
                null, "http://127.0.0.1:1/x", "DELETE", 5);
        when(internalMapper.selectList(any())).thenReturn(List.of());
        when(externalMapper.selectList(any())).thenReturn(List.of(badMethod));
        assertThatThrownBy(() -> factory.buildToolCallbacks(null))
                .as("仅支持 GET/POST/PUT，DELETE 应拒绝")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DELETE");
    }

    // ==================== 用例 5：图 wiring（bindTools → callModel 携带 ToolCallingChatOptions） ====================

    @Test
    @DisplayName("用例5: 绑定工具后 callModel 的 Prompt 携带 ToolCallingChatOptions 且含该回调；未绑定工具时与 Step2 行为一致（options=null）")
    void toolsBound_shouldCarryOptionsIntoPrompt_unbound_shouldKeepStep2Behavior() throws Exception {
        // 用工厂真实产出的回调（内部工具，mock bean）验证全链路
        AgentToolInternalConfig config = internalConfig("sum_tool", "求和工具",
                null, "echoToolBean", "execute");
        AgentToolInternalConfigMapper internalMapper = mock(AgentToolInternalConfigMapper.class);
        when(internalMapper.selectList(any())).thenReturn(List.of(config));
        AgentToolExternalConfigMapper externalMapper = mock(AgentToolExternalConfigMapper.class);
        when(externalMapper.selectList(any())).thenReturn(List.of());
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBean("echoToolBean")).thenReturn(new EchoToolBean());
        List<ToolCallback> callbacks = new AgentToolCallbackFactory(internalMapper, externalMapper, ctx)
                .buildToolCallbacks(null);

        CompiledGraph<AgentState> graph = new AgentGraphFactory().buildGraph();
        CapturingChatModel stub = new CapturingChatModel("工具回复");
        AgentGraphFactory.bindChatModel(stub);
        AgentGraphFactory.bindTools(callbacks);
        try {
            graph.invoke(Map.of("input", "hi", "chatModel", stub));
        } finally {
            AgentGraphFactory.clearChatModel();
            AgentGraphFactory.clearTools();
        }
        assertThat(stub.capturedPrompt.getOptions()).isInstanceOf(ToolCallingChatOptions.class);
        List<ToolCallback> inOptions = ((ToolCallingChatOptions) stub.capturedPrompt.getOptions()).getToolCallbacks();
        assertThat(inOptions).hasSize(1);
        assertThat(inOptions.get(0).getToolDefinition().name()).isEqualTo("sum_tool");

        // 未绑定工具：Prompt 构造与 Step2 完全一致（getOptions()=null，回执实测）
        CapturingChatModel plainStub = new CapturingChatModel("普通回复");
        AgentGraphFactory.bindChatModel(plainStub);
        try {
            graph.invoke(Map.of("input", "hi", "chatModel", plainStub));
        } finally {
            AgentGraphFactory.clearChatModel();
        }
        assertThat(plainStub.capturedPrompt.getOptions()).isNull();
    }

    // ==================== 测试数据工厂 ====================

    private AgentToolInternalConfig internalConfig(String name, String description, String inputSchema,
                                                   String beanName, String methodName) {
        AgentToolInternalConfig config = new AgentToolInternalConfig();
        config.setName(name);
        config.setDescription(description);
        config.setInputSchema(inputSchema);
        config.setBeanName(beanName);
        config.setMethodName(methodName);
        config.setEnabled(true);
        return config;
    }

    private AgentToolExternalConfig externalConfig(String name, String description, String inputSchema,
                                                   String url, String httpMethod, Integer timeoutSeconds) {
        AgentToolExternalConfig config = new AgentToolExternalConfig();
        config.setName(name);
        config.setDescription(description);
        config.setInputSchema(inputSchema);
        config.setUrl(url);
        config.setHttpMethod(httpMethod);
        config.setTimeoutSeconds(timeoutSeconds);
        config.setEnabled(true);
        return config;
    }

    private HttpServer startServer(ServerHandler handler) throws IOException {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        s.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            handler.handle(exchange, body);
        });
        s.start();
        return s;
    }

    /** 白名单内部工具 mock bean：约定签名 String execute(String params) */
    static class EchoToolBean {
        public String execute(String params) {
            return "echo:" + params;
        }
    }

    /** 记录收到的 Prompt 的 ChatModel 桩 */
    static class CapturingChatModel implements ChatModel {
        private final String reply;
        Prompt capturedPrompt;

        CapturingChatModel(String reply) {
            this.reply = reply;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            this.capturedPrompt = prompt;
            return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
        }
    }

    @FunctionalInterface
    private interface ServerHandler {
        void handle(HttpExchange exchange, String body) throws IOException;
    }
}
