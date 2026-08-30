package com.sw.ck.agent.orchestration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sw.ck.agent.entity.AgentModelConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ChatModelFactory} 测试（M07 Step2 §13.2，纯 JUnit，不启动 Spring 上下文）。
 * <p>
 * 重试行为用 JDK 内置 {@code com.sun.net.httpserver.HttpServer} 做行为验证（不引
 * 入 WireMock 等新测试依赖）：mock 服务前 N 次返回 500、第 N+1 次返回 200，通过
 * 实际请求次数断言 {@code retryCount} 生效（retryCount=2 → 最多 3 次尝试）。
 * 实测：Spring AI 1.0.4 对 500 响应重试（TransientAiException 语义），最后一次失败
 * 时抛出 {@code TransientAiException}。
 * </p>
 */
@DisplayName("动态模型客户端工厂测试")
class ChatModelFactoryTest {

    private static final String FAKE_API_KEY = "sk-test-123456";

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    @DisplayName("用例1: protocolType=openai → 构造出 OpenAiChatModel")
    void openai_shouldBuildOpenAiChatModel() {
        ChatModelFactory factory = new ChatModelFactory();

        ChatModel model = factory.build(config("openai", "http://127.0.0.1:1", 0), FAKE_API_KEY);

        assertThat(model).isInstanceOf(OpenAiChatModel.class);
    }

    @Test
    @DisplayName("用例2: protocolType=ollama → 构造出 OllamaChatModel")
    void ollama_shouldBuildOllamaChatModel() {
        ChatModelFactory factory = new ChatModelFactory();

        ChatModel model = factory.build(config("ollama", "http://127.0.0.1:1", 0), null);

        assertThat(model).isInstanceOf(OllamaChatModel.class);
    }

    @Test
    @DisplayName("用例3: protocolType 非法值 → 抛 IllegalArgumentException，不静默返回 null")
    void unknownProtocol_shouldThrow() {
        ChatModelFactory factory = new ChatModelFactory();

        assertThatThrownBy(() -> factory.build(config("other", "http://127.0.0.1:1", 0), FAKE_API_KEY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("other");
    }

    @Test
    @DisplayName("用例4: retryCount=2 → 前两次 500 后第三次 200，共 3 次尝试且最终成功")
    void retryCount_shouldAllowThreeAttempts() throws Exception {
        AtomicInteger lastHit = new AtomicInteger();
        server = startChatServer((exchange, n) -> {
            lastHit.set(n);
            if (n <= 2) {
                respond(exchange, 500, "{\"error\":{\"message\":\"boom\",\"type\":\"server_error\",\"code\":500}}");
            } else {
                respond(exchange, 200, chatCompletionJson("第三次成功"));
            }
        });
        ChatModelFactory factory = new ChatModelFactory();
        ChatModel model = factory.build(
                config("openai", "http://127.0.0.1:" + server.getAddress().getPort(), 2), FAKE_API_KEY);

        String reply = model.call(new Prompt("hello")).getResult().getOutput().getText();

        assertThat(reply).isEqualTo("第三次成功");
        assertThat(lastHit.get()).as("retryCount=2 应恰好尝试 3 次").isEqualTo(3);
    }

    @Test
    @DisplayName("用例4b: retryCount=0 → 服务 500 时仅尝试 1 次并抛异常")
    void retryCountZero_shouldAttemptOnce() throws Exception {
        AtomicInteger lastHit = new AtomicInteger();
        server = startChatServer((exchange, n) -> {
            lastHit.set(n);
            respond(exchange, 500, "{\"error\":{\"message\":\"boom\",\"type\":\"server_error\",\"code\":500}}");
        });
        ChatModelFactory factory = new ChatModelFactory();
        ChatModel model = factory.build(
                config("openai", "http://127.0.0.1:" + server.getAddress().getPort(), 0), FAKE_API_KEY);

        assertThatThrownBy(() -> model.call(new Prompt("hello"))).isInstanceOf(Exception.class);
        assertThat(lastHit.get()).as("retryCount=0 应只尝试 1 次").isEqualTo(1);
    }

    // ==================== 测试数据 / mock 服务 ====================

    private AgentModelConfig config(String protocol, String baseUrl, Integer retryCount) {
        AgentModelConfig config = new AgentModelConfig();
        config.setProtocolType(protocol);
        config.setBaseUrl(baseUrl);
        config.setModelName("gpt-4o");
        config.setRetryCount(retryCount);
        config.setTimeoutSeconds(10);
        return config;
    }

    private HttpServer startChatServer(Handler handler) throws IOException {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger hits = new AtomicInteger();
        s.createContext("/", exchange -> {
            int n = hits.incrementAndGet();
            handler.handle(exchange, n);
        });
        s.start();
        return s;
    }

    private void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private String chatCompletionJson(String content) {
        return "{\"id\":\"chatcmpl-test\",\"object\":\"chat.completion\",\"created\":1720000000,"
                + "\"model\":\"gpt-4o\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\","
                + "\"content\":\"" + content + "\"},\"finish_reason\":\"stop\"}],"
                + "\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":5,\"total_tokens\":8}}";
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange, int hitNumber) throws IOException;
    }
}
