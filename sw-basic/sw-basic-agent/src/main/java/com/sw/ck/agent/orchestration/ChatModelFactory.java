package com.sw.ck.agent.orchestration;

import com.sw.ck.agent.entity.AgentModelConfig;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;

/**
 * 动态模型客户端工厂（M07 Step2 "动态装载"）。
 * <p>
 * 给定一条 {@link AgentModelConfig}，按 {@code protocolType} 分支构造对应 Spring AI
 * {@link ChatModel}，并把 {@code temperature}/{@code maxTokens}/{@code topP}/
 * {@code timeoutSeconds}/{@code retryCount} 真实传入构造（Step1 仅落库存储，本类首次
 * 使其生效）。
 * </p>
 * <p>
 * 非 {@code @Component}：由 {@code AgentGraphAutoConfiguration} 手动 {@code new} 后注册
 * 为 Bean。协议白名单固定 2 分支（openai/ollama），非法协议显式抛
 * {@link IllegalArgumentException}，不允许静默返回 null（方案 §10 约束 1）。
 * </p>
 * <p>
 * <b>明文 API Key 生命周期</b>：{@code plainApiKey} 仅用于本次构造（放入
 * {@code OpenAiApi.Builder}），不落任何字段、不打日志；无 Key（如本地无鉴权网关）时
 * {@code OpenAiApi.Builder.build()} 会断言 apiKey 非 null（实测），传空串满足构造，
 * 请求头退化为 {@code "Bearer "}。
 * </p>
 */
public class ChatModelFactory {

    /** 协议白名单（固定 2 分支，非可插拔注册表，理由见方案 §9.1） */
    private static final Set<String> SUPPORTED_PROTOCOLS = Set.of("openai", "ollama");

    /** timeoutSeconds 未配置时的默认值（秒），与 V19 表结构 DEFAULT 30 一致 */
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    /**
     * 按配置构造模型客户端。
     *
     * @param config      大模型接入配置（Step1 产物，只读）
     * @param plainApiKey 解密后的明文 API Key，可为 null/空（无鉴权场景）
     * @throws IllegalArgumentException 协议类型不在白名单内
     */
    public ChatModel build(AgentModelConfig config, String plainApiKey) {
        String protocol = config.getProtocolType();
        if (protocol == null || !SUPPORTED_PROTOCOLS.contains(protocol)) {
            throw new IllegalArgumentException("不支持的协议类型，无法构造模型客户端: " + protocol);
        }
        return switch (protocol) {
            case "openai" -> buildOpenAi(config, plainApiKey);
            case "ollama" -> buildOllama(config);
            // 防御性分支：SUPPORTED_PROTOCOLS 已兜底，理论不可达（方案 §9.1 要求第三个分支必须存在）
            default -> throw new IllegalStateException("不应到达: " + protocol);
        };
    }

    private ChatModel buildOpenAi(AgentModelConfig config, String plainApiKey) {
        OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
                .baseUrl(config.getBaseUrl())
                .restClientBuilder(buildRestClientBuilder(config));
        // OpenAiApi.Builder.build() 断言 apiKey 非 null（实测）；明文 Key 非空才传，空则传空串满足构造
        apiBuilder.apiKey(plainApiKey != null && !plainApiKey.isEmpty() ? plainApiKey : "");
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(config.getModelName())
                .temperature(toDouble(config.getTemperature()))
                .maxTokens(config.getMaxTokens())
                .topP(toDouble(config.getTopP()))
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(apiBuilder.build())
                .defaultOptions(options)
                .retryTemplate(buildRetryTemplate(config.getRetryCount()))
                .build();
    }

    private ChatModel buildOllama(AgentModelConfig config) {
        OllamaApi.Builder apiBuilder = OllamaApi.builder()
                .baseUrl(config.getBaseUrl())
                .restClientBuilder(buildRestClientBuilder(config));
        OllamaOptions options = OllamaOptions.builder()
                .model(config.getModelName())
                .temperature(toDouble(config.getTemperature()))
                .topP(toDouble(config.getTopP()))
                // Ollama 无 maxTokens 字段（实测 OllamaOptions.Builder 无 maxTokens setter），
                // 对应字段为 numPredict（方案 §9.1 推测与实际 API 的偏差）
                .numPredict(config.getMaxTokens())
                .build();
        return OllamaChatModel.builder()
                .ollamaApi(apiBuilder.build())
                .defaultOptions(options)
                .retryTemplate(buildRetryTemplate(config.getRetryCount()))
                .build();
    }

    /**
     * timeoutSeconds 真实生效：通过 {@code RestClient.Builder} 注入
     * {@link SimpleClientHttpRequestFactory} 的 connect/read 超时（实测对
     * OpenAiApi/OllamaApi 均生效）。
     */
    private RestClient.Builder buildRestClientBuilder(AgentModelConfig config) {
        int timeoutSeconds = config.getTimeoutSeconds() == null
                ? DEFAULT_TIMEOUT_SECONDS
                : Math.max(1, config.getTimeoutSeconds());
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        return RestClient.builder().requestFactory(requestFactory);
    }

    /**
     * 重试策略：attempts = max(0, retryCount) + 1（retryCount=2 → 最多 3 次尝试）。
     */
    private RetryTemplate buildRetryTemplate(Integer retryCount) {
        int attempts = (retryCount == null ? 0 : Math.max(0, retryCount)) + 1;
        return RetryTemplate.builder().maxAttempts(attempts).build();
    }

    /** BigDecimal → Double（null 时不设置，走模型提供方默认值） */
    private Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
