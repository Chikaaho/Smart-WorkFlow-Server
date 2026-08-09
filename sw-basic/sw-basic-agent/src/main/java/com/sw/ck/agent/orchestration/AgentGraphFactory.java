package com.sw.ck.agent.orchestration;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
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
        Prompt prompt;
        if (tools != null && !tools.isEmpty()) {
            // internalToolExecutionEnabled 未显式设置时默认 true（§9.2 实测
            // DefaultToolExecutionEligibilityPredicate + isInternalToolExecutionEnabled），
            // tool_calls 自动执行，无需显式开启
            ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                    .toolCallbacks(tools)
                    .build();
            prompt = new Prompt(input, options);
        } else {
            prompt = new Prompt(input);
        }
        ChatResponse response = chatModel.call(prompt);
        String output = response.getResult().getOutput().getText();
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
