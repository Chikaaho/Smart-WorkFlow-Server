package com.sw.ck.agent.orchestration;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 最小编排图工厂（M07 Step2）：单节点 {@code StateGraph<AgentState>}，
 * {@code START → callModel → END}，节点内调用一次模型并提取回复文本。
 * <p>
 * 无状态实例方法 {@link #buildGraph()}，供 {@code AgentGraphAutoConfiguration} 注册
 * 单例 {@code CompiledGraph} Bean 与单元测试直接调用（纯 JUnit 可测，无需 Spring 上下文）。
 * </p>
 * <p>
 * <b>与 LangGraph4j 1.5.14 实测行为的两个偏差（详见执行回执）</b>：
 * </p>
 * <ol>
 *   <li>channel 默认值不能为 null：{@code Channels.base(() -> null)} 会在
 *       {@code getInitialStateFromSchema} 处 NPE（Collectors.toMap 拒绝 null 值），
 *       因此 input/output 用空串默认值；chatModel 无法给非空默认，改用 last-wins
 *       reducer（无默认值）</li>
 *   <li>节点间状态流转会对 state 做深拷贝（默认 Java 序列化），{@link ChatModel}
 *       不可序列化，直接放 state 会抛 {@code NotSerializableException}：本类用
 *       {@link SparseStateSerializer} 在序列化时跳过 chatModel，读取时从线程绑定
 *       （{@link #bindChatModel}/{@link #clearChatModel}）重新挂载</li>
 * </ol>
 */
public class AgentGraphFactory {

    /** 模型调用节点名 */
    public static final String NODE_CALL_MODEL = "callModel";

    /**
     * 当前执行线程绑定的 ChatModel（graph 状态机不可序列化 ChatModel，由调用方在
     * {@code invoke()} 前绑定、finally 中清除；ThreadLocal 天然线程隔离，并发安全）。
     */
    private static final ThreadLocal<ChatModel> CHAT_MODEL_BINDING = new ThreadLocal<>();

    /**
     * 当前执行线程绑定的工具回调列表（M07 Step3 工具沙箱）。工具不进入 graph state
     * （{@link SparseStateSerializer} 只序列化 input/output），与 ChatModel 同款
     * ThreadLocal 绑定模式：调用方 invoke 前 {@link #bindTools}、finally 中
     * {@link #clearTools}。未绑定时 callModel 行为与 Step2 完全一致（向后兼容）。
     */
    private static final ThreadLocal<List<ToolCallback>> TOOL_CALLBACKS_BINDING = new ThreadLocal<>();

    /**
     * 当前执行线程绑定的历史消息（M07 Step4 F04 多轮会话）。callModel 节点读取后与
     * 本轮新 {@link UserMessage} 合并构造 Prompt，LLM 获得多轮上下文。与 ChatModel
     * 同款 ThreadLocal 生命周期：调用方 invoke 前 {@link #bindHistoryMessages}、
     * finally 中 {@link #clearHistoryMessages}。历史消息不经 graph state
     * （SparseStateSerializer 不序列化），与 tools ThreadLocal 同一模式。
     */
    private static final ThreadLocal<List<Message>> HISTORY_MESSAGES_BINDING = new ThreadLocal<>();

    /**
     * 当前执行线程绑定的工具调用记录载体（M07 Step4 F04）。{@link AgentToolCallbackFactory}
     * 的工具 lambda 包装在每次实际调用后向该列表追加 {@link ToolCallRecord}（未绑定时
     * 跳过，不影响 Step3 行为）；编排 ServiceImpl 在 invoke 后读取并逐条落库。
     * package-private：仅同包 AgentToolCallbackFactory 写入（lambda 包装点）。
     */
    static final ThreadLocal<List<ToolCallRecord>> TOOL_CALL_RECORDS_BINDING = new ThreadLocal<>();

    /**
     * 当前执行线程绑定的 Token 使用量（M07-F04-02）。callModel 节点从
     * {@link ChatResponse} 提取 usage 后写入此 ThreadLocal；编排 ServiceImpl
     * 在 invoke 后读取并持久化。不进入 graph state（SparseStateSerializer 不序列化），
     * 与 tools/historyMessages ThreadLocal 同一模式。
     */
    private static final ThreadLocal<UsageSnapshot> TOKEN_USAGE_BINDING = new ThreadLocal<>();

    /** 绑定本次执行的 ChatModel（invoke 前调用） */
    public static void bindChatModel(ChatModel chatModel) {
        CHAT_MODEL_BINDING.set(chatModel);
    }

    /** 清除本次执行的 ChatModel 绑定（invoke 结束后 finally 调用） */
    public static void clearChatModel() {
        CHAT_MODEL_BINDING.remove();
    }

    /** 绑定本次执行的工具回调列表（invoke 前调用；空列表时无效果，行为同未绑定） */
    public static void bindTools(List<ToolCallback> tools) {
        TOOL_CALLBACKS_BINDING.set(tools);
    }

    /** 清除本次执行的工具回调绑定（invoke 结束后 finally 调用，防 ThreadLocal 泄漏） */
    public static void clearTools() {
        TOOL_CALLBACKS_BINDING.remove();
    }

    /** 绑定本次执行的历史消息（invoke 前调用；null/空列表时 callModel 行为与 Step2/3 一致） */
    public static void bindHistoryMessages(List<Message> messages) {
        HISTORY_MESSAGES_BINDING.set(messages);
    }

    /** 清除本次执行的历史消息绑定（invoke 结束后 finally 调用，防 ThreadLocal 泄漏） */
    public static void clearHistoryMessages() {
        HISTORY_MESSAGES_BINDING.remove();
    }

    /** 绑定工具调用记录载体（invoke 前调用，由 ServiceImpl 传入空列表） */
    public static void bindToolCallRecords(List<ToolCallRecord> records) {
        TOOL_CALL_RECORDS_BINDING.set(records);
    }

    /** 清除工具调用记录载体（invoke 结束后 finally 调用） */
    public static void clearToolCallRecords() {
        TOOL_CALL_RECORDS_BINDING.remove();
    }

    /** 读取本次执行捕获的工具调用记录（ServiceImpl 在 invoke 后调用；未绑定返回 null） */
    public static List<ToolCallRecord> getToolCallRecords() {
        return TOOL_CALL_RECORDS_BINDING.get();
    }

    /** Token 使用量快照（M07-F04-02），callModel 从 ChatResponse 提取后写入 ThreadLocal */
    public record UsageSnapshot(Long inputTokens, Long outputTokens) {}

    /** 存储 Token 使用量（callModel 节点调用） */
    static void storeTokenUsage(Long inputTokens, Long outputTokens) {
        TOKEN_USAGE_BINDING.set(new UsageSnapshot(inputTokens, outputTokens));
    }

    /** 清除 Token 使用量（invoke 结束后 finally 调用） */
    public static void clearTokenUsage() {
        TOKEN_USAGE_BINDING.remove();
    }

    /** 读取本次执行的 Token 使用量（ServiceImpl 在 invoke 后调用；未绑定返回 null） */
    public static UsageSnapshot getTokenUsage() {
        return TOKEN_USAGE_BINDING.get();
    }

    /**
     * 构造并编译最小图。
     *
     * @throws GraphStateException 图构造/编译失败（节点重名、边非法等）
     */
    public CompiledGraph<AgentState> buildGraph() throws GraphStateException {
        Map<String, Channel<?>> channels = Map.of(
                "input", Channels.base(() -> ""),
                "output", Channels.base(() -> ""),
                "chatModel", Channels.base((Object prev, Object next) -> next));
        StateGraph<AgentState> graph = new StateGraph<>(channels, new SparseStateSerializer());
        graph.addNode(NODE_CALL_MODEL, AsyncNodeAction.node_async(this::callModel));
        graph.addEdge(StateGraph.START, NODE_CALL_MODEL);
        graph.addEdge(NODE_CALL_MODEL, StateGraph.END);
        return graph.compile();
    }

    /**
     * 节点动作：取 chatModel 与 input → 调用一次模型 → 提取回复文本写入 output。
     * <p>
     * 响应文本提取链（Spring AI 公开 API 实测）：{@code ChatResponse.getResult()}
     * → {@code Generation.getOutput()}（{@code AssistantMessage}）→ {@code getText()}。
     * </p>
     * <p>
     * M07 Step3：绑定了工具回调时，Prompt 携带 {@link ToolCallingChatOptions}（工具经
     * options 传入，Prompt 无工具重载，前置调研 §3.2 实测）；未绑定时构造与 Step2
     * 完全相同的 {@code new Prompt(input)}（向后兼容）。tool_calls 的执行循环内建于
     * {@code ChatModel.call()}（Spring AI internalCall 递归 + ToolCallingManager），
     * 本节点不写自循环、不改图拓扑。
     * </p>
     */
    private Map<String, Object> callModel(AgentState state) throws Exception {
        ChatModel chatModel = (ChatModel) state.value("chatModel")
                .orElseThrow(() -> new IllegalStateException("初始状态缺少 chatModel"));
        String input = (String) state.value("input")
                .orElseThrow(() -> new IllegalStateException("初始状态缺少 input"));
        List<ToolCallback> tools = TOOL_CALLBACKS_BINDING.get();
        // M07 Step4 F04：历史消息（ThreadLocal 注入） + 本轮新 UserMessage 构造完整消息列表。
        // 历史为空/null 时 messages 仅含新 UserMessage——与 Step2/3 的 new Prompt(input)
        // 语义等价（Prompt(String) 内部即 new Prompt(UserMessage(input))），向后兼容。
        List<Message> history = HISTORY_MESSAGES_BINDING.get();
        List<Message> messages = new ArrayList<>();
        if (history != null) {
            messages.addAll(history);
        }
        messages.add(new UserMessage(input));
        Prompt prompt;
        if (tools != null && !tools.isEmpty()) {
            // internalToolExecutionEnabled 未显式设置时默认 true（§9.2 实测
            // DefaultToolExecutionEligibilityPredicate + isInternalToolExecutionEnabled），
            // tool_calls 自动执行，无需显式开启
            ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                    .toolCallbacks(tools)
                    .build();
            prompt = new Prompt(messages, options);
        } else {
            prompt = new Prompt(messages);
        }
        ChatResponse response = chatModel.call(prompt);
        String output = response.getResult().getOutput().getText();
        // M07-F04-02: 提取供应商返回的 usage 数据，通过 ThreadLocal 传递（不进 graph state）
        // 供应商缺失/部分缺失 usage 时保持 null 语义（不写零、不估算）——经
        // TokenUsageResolver 读取原生字段，避免 DefaultUsage 的 null→0 归一
        Long[] tokens = TokenUsageResolver.resolve(
                response.getMetadata() != null ? response.getMetadata().getUsage() : null);
        storeTokenUsage(tokens[0], tokens[1]);
        return Map.of("output", output);
    }

    /**
     * 稀疏状态序列化器：LangGraph4j 在节点间对 state 做深拷贝（StateSerializer.cloneObject），
     * 只序列化可序列化的 input/output；chatModel 在 write 时跳过、read 时从线程绑定重新挂载，
     * 保证节点内 {@code state.value("chatModel")} 可见且不触发 NotSerializableException。
     */
    static class SparseStateSerializer extends StateSerializer<AgentState> {

        SparseStateSerializer() {
            super(AgentState::new);
        }

        @Override
        public void write(AgentState object, ObjectOutput out) throws IOException {
            out.writeObject(object.value("input").orElse(""));
            out.writeObject(object.value("output").orElse(""));
        }

        @Override
        public AgentState read(ObjectInput in) throws IOException, ClassNotFoundException {
            Map<String, Object> data = new HashMap<>();
            data.put("input", in.readObject());
            data.put("output", in.readObject());
            ChatModel bound = CHAT_MODEL_BINDING.get();
            if (bound != null) {
                data.put("chatModel", bound);
            }
            return stateOf(data);
        }
    }
}
