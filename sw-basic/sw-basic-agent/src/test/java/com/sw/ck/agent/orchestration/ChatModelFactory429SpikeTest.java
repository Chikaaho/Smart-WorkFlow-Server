package com.sw.ck.agent.orchestration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sw.ck.agent.entity.AgentModelConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M07-Step5 §5 V1/V2 spike 验证：真实 HTTP 429 响应走
 * {@link ChatModelFactory#build} → {@link ChatModel#call} 全链路。
 * <p>
 * 背景（前置调研）：Spring AI 1.0.4 默认错误处理器（{@code RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER}）
 * 对 4xx 抛 {@link NonTransientAiException}、5xx 抛 {@code TransientAiException}（该结论本次实测确认）。
 * 本测试回答两个问题：
 * </p>
 * <ul>
 *   <li><b>V1</b>：429 异常以什么类型链到达测试 catch 块；沿 cause 链查找
 *       {@link RestClientResponseException} 且 {@code getStatusCode().value()==429} 是否成立。</li>
 *   <li><b>V2</b>：{@code ChatModelFactory.buildRetryTemplate}（attempts = retryCount + 1）对 429
 *       是否触发重试——用 mock 服务器请求计数对照 retryCount 配置值。</li>
 * </ul>
 * <p>
 * 手法与 {@link ChatModelFactoryTest} 一致：JDK 内置 {@code com.sun.net.httpserver.HttpServer}
 * 起本地 mock 服务（不引入 WireMock 等新依赖），纯 JUnit 不启动 Spring 上下文。
 * 测试必须走真实 HTTP 链路，不得构造假异常对象。
 * </p>
 */
@DisplayName("M07-Step5 spike：真实 HTTP 429 异常链与重试行为")
class ChatModelFactory429SpikeTest {

    private static final String FAKE_API_KEY = "sk-test-fake";

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    @DisplayName("V1/V2: 真实 429 → 异常类型链输出 + RestClientResponseException(429) 判断 + 重试计数")
    void testRealHttp429ExceptionChain() throws Exception {
        // mock 服务器：每个请求计数 +1，记录每次命中的时间戳（验证重试间是否有 backoff 延迟），恒定返回 HTTP 429
        AtomicInteger hits = new AtomicInteger();
        long[] hitTimes = new long[16];
        server = startServer(exchange -> {
            long t = System.nanoTime();
            int n = hits.incrementAndGet();
            if (n < hitTimes.length) {
                hitTimes[n] = t;
            }
            respond(exchange, 429, "too many requests");
        });

        // 按现场 AgentModelConfig 字段构造：protocolType=openai，baseUrl 指向本地 mock
        //（OpenAiApi 拼路径时 baseUrl 后追加 completionsPath，HttpServer context "/" 全匹配，无需 /v1）
        AgentModelConfig config = new AgentModelConfig();
        config.setProtocolType("openai");
        config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        config.setModelName("gpt-4o");
        config.setRetryCount(2);          // buildRetryTemplate: attempts = max(0, 2) + 1 = 3
        config.setTimeoutSeconds(5);
        config.setEnabled(true);

        ChatModel model = new ChatModelFactory().build(config, FAKE_API_KEY);

        long t0 = System.nanoTime();
        Throwable thrown = null;
        try {
            model.call(new Prompt("hello"));
        } catch (Throwable t) {
            thrown = t;
        }
        long tEnd = System.nanoTime();
        int total = hits.get();
        long firstHitTime = total >= 1 ? hitTimes[1] : tEnd;
        long lastHitTime = total >= 1 ? hitTimes[total] : t0;
        long gap1Ms = total >= 2 ? (hitTimes[2] - hitTimes[1]) / 1_000_000 : -1;
        long gap2Ms = total >= 3 ? (hitTimes[3] - hitTimes[2]) / 1_000_000 : -1;

        // ==================== V1：打印完整 cause 链 + 查找 RestClientResponseException(429) ====================
        boolean found429RestClientException = false;
        StringBuilder chain = new StringBuilder();
        Throwable cur = thrown;
        int depth = 0;
        while (cur != null && depth < 20) {
            chain.append("  [").append(depth).append("] ").append(cur.getClass().getName())
                    .append(" :: ").append(cur.getMessage()).append('\n');
            if (cur instanceof RestClientResponseException rcre && rcre.getStatusCode().value() == 429) {
                found429RestClientException = true;
            }
            cur = cur.getCause();
            depth++;
        }
        // V1 实测替代判断途径：链顶为 NonTransientAiException 且消息含 "429"（消息格式 "%s - %s"）
        boolean foundNonTransient429 = thrown instanceof NonTransientAiException
                && thrown.getMessage() != null && thrown.getMessage().contains("429");

        System.out.println("[spike] ====== 真实 HTTP 429 全链路结果 (retryCount=2 → maxAttempts=3) ======");
        System.out.println("[spike] 异常类型链:");
        System.out.println(chain);
        System.out.println("[spike] 沿 cause 链找到 RestClientResponseException(status=429): " + found429RestClientException);
        System.out.println("[spike] 替代途径 NonTransientAiException + 消息含 429: " + foundNonTransient429);
        System.out.println("[spike] 服务器实际请求计数 hits=" + hits.get() + "（对照 maxAttempts=3）");
        System.out.println("[spike] 总耗时 " + (tEnd - t0) / 1_000_000 + "ms");
        System.out.println("[spike] 首次请求前耗时 " + (firstHitTime - t0) / 1_000_000 + "ms"
                + "（call() 内部 HTTP 请求发出前，非重试延迟）");
        System.out.println("[spike] 命中间隔: " + gap1Ms + "ms, " + gap2Ms + "ms"
                + "（buildRetryTemplate 无 backoff 配置，预期接近 0；实测值含连接重建等开销）");
        System.out.println("[spike] 末次命中后到异常抛出耗时 " + (tEnd - lastHitTime) / 1_000_000 + "ms");

        // ==================== 断言固化结论 ====================
        assertThat(thrown).as("429 必须抛异常").isNotNull();
        // V1 实测：Spring AI 1.0.4 默认错误处理器直接抛 NonTransientAiException，
        // cause 链中不存在 RestClientResponseException(429)——按实测如实断言 false
        assertThat(found429RestClientException)
                .as("V1: 链中含 RestClientResponseException(status=429) 的假设不成立（默认错误处理器直接抛 NonTransientAiException）")
                .isFalse();
        // V1 实测可行的判断途径
        assertThat(foundNonTransient429)
                .as("V1 替代途径: 异常 instanceof NonTransientAiException 且 getMessage() 含 \"429\"")
                .isTrue();
        // V2 实测：ChatModelFactory 自建 RetryTemplate（RetryTemplate.builder() 默认分类器
        // 对任意 Exception 重试）对 429 同样重试，retryCount=2 → 恰好 3 次尝试
        assertThat(hits.get()).as("V2: 429 被 RetryTemplate 重试, retryCount=2 → 3 次尝试").isEqualTo(3);
    }

    // ==================== mock 服务 ====================

    private HttpServer startServer(Handler handler) throws IOException {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        s.createContext("/", exchange -> handler.handle(exchange));
        s.start();
        return s;
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
