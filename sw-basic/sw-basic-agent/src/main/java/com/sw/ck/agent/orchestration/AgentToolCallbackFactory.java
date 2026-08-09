package com.sw.ck.agent.orchestration;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sw.ck.agent.entity.tool.AgentToolExternalConfig;
import com.sw.ck.agent.entity.tool.AgentToolInternalConfig;
import com.sw.ck.agent.mapper.tool.AgentToolExternalConfigMapper;
import com.sw.ck.agent.mapper.tool.AgentToolInternalConfigMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 工具回调工厂（M07 Step3 工具沙箱）：从 DB 白名单表加载启用工具，构造
 * {@link ToolCallback} 列表。两类工具统一走 {@link FunctionToolCallback} lambda 路径
 * （不使用注解式工具声明、不用 MethodToolCallback），工具名/描述/inputSchema 三字段
 * 均来自 DB 配置，不在代码硬编。
 * <p>
 * <b>安全边界</b>：{@code beanName}/{@code methodName}（内部工具）与
 * {@code url}/{@code httpMethod}（外部工具）均来自 DB 白名单表，不接受运行时外部
 * 传入——LLM 的 tool_calls 只携带工具名与参数，名称 → 目标 的映射在本类内部完成。
 * </p>
 * <p>
 * <b>fail-fast</b>：beanName 不在 ApplicationContext（{@link NoSuchBeanDefinitionException}）
 * 或 methodName 不存在（{@link IllegalStateException}）在工厂构造阶段即抛出，不等到
 * LLM 调用时才暴露；外部工具 HTTP 方法仅支持 GET/POST/PUT。
 * </p>
 * <p>
 * <b>入参约定（现场 spike 实测，见回执 §3）</b>：{@code inputType(String.class)} 时
 * Spring AI 会把 tool_calls 的 arguments 反序列化为 String——LLM 按生成的
 * {@code {"type":"string"}} schema 发送 JSON 字符串字面量，lambda 收到原始参数字符串；
 * {@code inputSchema(null)} 安全（回退由 inputType 生成 schema）。若某配置的
 * input_schema 诱导 LLM 发送 JSON 对象，{@code call()} 抛 Jackson 反序列化异常，由
 * Spring AI 默认 ToolExecutionExceptionProcessor 转为错误消息回喂 LLM，不中断调用。
 * </p>
 * <p>
 * 非启动缓存：每次 orchestration 调用时加载（MyBatis-Plus 层有缓存支持），保证工具
 * 配置变更即时生效（方案 §8.1）。
 * </p>
 */
@Component
@ConditionalOnProperty(prefix = "sw.agent", name = "enabled", havingValue = "true")
public class AgentToolCallbackFactory {

    /** timeoutSeconds 未配置时的默认值（秒），与 V20 表结构 DEFAULT 30 一致 */
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    /** 外部工具支持的 HTTP 方法白名单 */
    private static final Set<String> SUPPORTED_HTTP_METHODS = Set.of("GET", "POST", "PUT");

    private final AgentToolInternalConfigMapper internalMapper;
    private final AgentToolExternalConfigMapper externalMapper;
    private final ApplicationContext applicationContext;

    public AgentToolCallbackFactory(AgentToolInternalConfigMapper internalMapper,
                                    AgentToolExternalConfigMapper externalMapper,
                                    ApplicationContext applicationContext) {
        this.internalMapper = internalMapper;
        this.externalMapper = externalMapper;
        this.applicationContext = applicationContext;
    }

    /**
     * 加载启用工具并构造回调列表（内部 + 外部）。
     *
     * @param tenantId 租户过滤条件；null 时不显式过滤（生产环境由 MyBatis-Plus
     *                 租户拦截器按当前登录租户自动隔离，同 Step2 先例）
     * @return 工具回调列表（可能为空）
     */
    public List<ToolCallback> buildToolCallbacks(Long tenantId) {
        List<AgentToolInternalConfig> internals = internalMapper.selectList(
                Wrappers.<AgentToolInternalConfig>lambdaQuery()
                        .eq(AgentToolInternalConfig::getEnabled, true)
                        .eq(tenantId != null, AgentToolInternalConfig::getTenantId, tenantId)
                        .orderByDesc(AgentToolInternalConfig::getId));
        List<AgentToolExternalConfig> externals = externalMapper.selectList(
                Wrappers.<AgentToolExternalConfig>lambdaQuery()
                        .eq(AgentToolExternalConfig::getEnabled, true)
                        .eq(tenantId != null, AgentToolExternalConfig::getTenantId, tenantId)
                        .orderByDesc(AgentToolExternalConfig::getId));
        List<ToolCallback> callbacks = new ArrayList<>(internals.size() + externals.size());
        for (AgentToolInternalConfig config : internals) {
            callbacks.add(buildInternalCallback(config));
        }
        for (AgentToolExternalConfig config : externals) {
            callbacks.add(buildExternalCallback(config));
        }
        return callbacks;
    }

    /**
     * 构造内部工具回调：运行时反射调用白名单 bean 的约定方法
     * {@code String execute(String params)}（构造阶段完成 bean 解析与签名校验，fail-fast）。
     */
    private ToolCallback buildInternalCallback(AgentToolInternalConfig config) {
        // beanName 来自 DB 白名单，不存在时 getBean 抛 NoSuchBeanDefinitionException（fail-fast）
        Object bean = applicationContext.getBean(config.getBeanName());
        Method method;
        try {
            method = bean.getClass().getMethod(config.getMethodName(), String.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    "内部工具方法不存在（白名单配置错误，须 String 入参签名）: "
                            + config.getBeanName() + "." + config.getMethodName() + "(String)",
                    e);
        }
        if (!String.class.equals(method.getReturnType())) {
            // 约定：白名单方法签名 = String execute(String params)，不符即拒绝（禁止静默失败）
            throw new IllegalStateException(
                    "内部工具方法返回类型与约定不符（须返回 String）: "
                            + config.getBeanName() + "." + config.getMethodName()
                            + " 实际返回 " + method.getReturnType().getSimpleName());
        }
        return FunctionToolCallback.builder(config.getName(), (String jsonArgs) -> {
                    try {
                        return (String) method.invoke(bean, jsonArgs);
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException("内部工具调用失败: " + config.getName(), e);
                    } catch (InvocationTargetException e) {
                        // 目标方法真实异常（原因链展开），由框架 ToolExecutionExceptionProcessor
                        // 转为错误消息回喂 LLM，不中断整个调用
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        throw new IllegalStateException("内部工具执行失败: " + config.getName()
                                + " - " + cause.getMessage(), cause);
                    }
                })
                .description(config.getDescription())
                // 实测（回执 §3.4）：inputSchema(null) 不 NPE，回退由 inputType 生成 {"type":"string"}
                .inputSchema(config.getInputSchema())
                .inputType(String.class)
                .build();
    }

    /**
     * 构造外部工具回调：按白名单 URL + HTTP 方法发起 RestClient 请求，超时从
     * {@code timeoutSeconds} 字段读（参照 {@link ChatModelFactory#buildRestClientBuilder} 模式）。
     */
    private ToolCallback buildExternalCallback(AgentToolExternalConfig config) {
        String httpMethodName = config.getHttpMethod() == null
                ? "POST"
                : config.getHttpMethod().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_HTTP_METHODS.contains(httpMethodName)) {
            throw new IllegalArgumentException(
                    "外部工具 HTTP 方法不支持（仅 GET/POST/PUT）: " + config.getHttpMethod());
        }
        int timeoutSeconds = config.getTimeoutSeconds() == null
                ? DEFAULT_TIMEOUT_SECONDS
                : Math.max(1, config.getTimeoutSeconds());
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        RestClient restClient = RestClient.builder().requestFactory(requestFactory).build();
        HttpMethod httpMethod = HttpMethod.valueOf(httpMethodName);
        String url = config.getUrl();
        return FunctionToolCallback.builder(config.getName(), (String jsonArgs) -> {
                    // 实测（回执 §3.5）：GET 携带 body 不报错（SimpleClientHttpRequestFactory 忽略），
                    // 因此统一携带 body，无需按方法分支
                    return restClient.method(httpMethod)
                            .uri(url)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(jsonArgs)
                            .retrieve()
                            .body(String.class);
                })
                .description(config.getDescription())
                .inputSchema(config.getInputSchema())
                .inputType(String.class)
                .build();
    }
}
