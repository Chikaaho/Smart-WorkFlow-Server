package com.sw.ck.agent.orchestration;

import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;

/**
 * 供应商 usage → input/output Token 的解析辅助（M07-F04-02）。
 * <p>
 * Spring AI 的 {@link DefaultUsage} 构造器会把 null 的 prompt/completion 归一为 0
 * （字节码实证：null → {@code Integer.valueOf(0)}），且 {@link EmptyUsage} 恒返回 0——
 * 因此直接调用 {@code usage.getPromptTokens()/getCompletionTokens()} 会把"供应商未返回"
 * 或"部分缺失"的语义伪装成明确 0，违反本功能"未知 ≠ 0"的产品口径。
 * </p>
 * <p>
 * 解法：优先读取 {@code usage.getNativeUsage()} 中的原始供应商字段（OpenAI 的
 * {@code OpenAiApi.Usage} record，缺失字段保持 null），缺失返回 null；nativeUsage
 * 不可用时（其他协议/Ollama）退化为 {@code Usage} 接口值，仅排除 EmptyUsage 伪零。
 * </p>
 */
final class TokenUsageResolver {

    private TokenUsageResolver() {
    }

    /**
     * 从 usage 解析输入/输出 Token（独立 null 语义）。
     *
     * @param usage Spring AI 响应 metadata 中的 usage（可为 null）
     * @return [inputTokens, outputTokens]；供应商缺失或未返回时对应侧为 null
     */
    static Long[] resolve(Usage usage) {
        if (usage == null || usage instanceof EmptyUsage) {
            // 未返回 usage / Spring AI 伪零占位 → 两侧均为未知
            return new Long[] { null, null };
        }
        return resolveInner(usage);
    }

    private static Long[] resolveInner(Usage usage) {
        // 优先原生字段（OpenAI Usage record：缺失字段为 null，不经过 DefaultUsage 0 归一）
        Object nativeUsage = usage.getNativeUsage();
        if (nativeUsage != null) {
            Long input = nativeToken(nativeUsage, "promptTokens", "prompt_tokens");
            Long output = nativeToken(nativeUsage, "completionTokens", "completion_tokens");
            if (input != null || output != null) {
                return new Long[] { input, output };
            }
        }
        // 退化路径：接口值 + 排除 EmptyUsage 后（其余实现如 Ollama 无 0 归一问题）
        Long input = usage.getPromptTokens() != null ? usage.getPromptTokens().longValue() : null;
        Long output = usage.getCompletionTokens() != null ? usage.getCompletionTokens().longValue() : null;
        return new Long[] { input, output };
    }

    /** 反射读取 record 访问器（OpenAiApi.Usage.promptTokens()/completionTokens()）或 Map 键 */
    private static Long nativeToken(Object nativeUsage, String accessorName, String mapKey) {
        if (nativeUsage instanceof java.util.Map<?, ?> map) {
            Object value = map.get(mapKey);
            return value instanceof Number n ? n.longValue() : null;
        }
        try {
            // getDeclaredMethod + setAccessible：覆盖非 public 类中的 public 访问器
            // （record 访问器总是存在；OpenAiApi.Usage 为 public record，此处兜底兼容）
            java.lang.reflect.Method accessor = nativeUsage.getClass().getDeclaredMethod(accessorName);
            accessor.setAccessible(true);
            Object value = accessor.invoke(nativeUsage);
            return value instanceof Number n ? n.longValue() : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
