package com.sw.ck.agent.orchestration;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * {@link AgentGraphFactory} 测试（M07 Step2 §13.2，纯 JUnit，不启动 Spring 上下文）。
 * <p>
 * 用例 3 断言依据：langgraph4j 1.5.14 实测，节点动作抛异常时 {@code invoke()} 以
 * {@code java.util.concurrent.CompletionException}（CompletableFuture.join 包装链：
 * CompletionException → ExecutionException → 原始异常）原样抛出，不会返回空
 * {@code Optional}——断言只锁定根因类型，对包装层宽松（兼容未来版本包装变化）。
 * </p>
 */
@DisplayName("最小编排图构造/执行测试")
class AgentGraphFactoryTest {

    @Test
    @DisplayName("用例1: buildGraph() 不抛异常，返回非 null CompiledGraph")
    void buildGraph_shouldCompile() throws Exception {
        CompiledGraph<AgentState> graph = new AgentGraphFactory().buildGraph();

        assertThat(graph).isNotNull();
    }

    @Test
    @DisplayName("用例2: 绑定 stub ChatModel 后 invoke，output 与 stub 回复一致（START→callModel→END 全链路）")
    void invoke_withStubChatModel_shouldReturnOutput() throws Exception {
        CompiledGraph<AgentState> graph = new AgentGraphFactory().buildGraph();
        ChatModel stub = new StubChatModel("你好，stub 回复");

        AgentGraphFactory.bindChatModel(stub);
        try {
            Optional<AgentState> result = graph.invoke(Map.of("input", "hello", "chatModel", stub));

            assertThat(result).isPresent();
            assertThat(result.get().value("output")).hasValue("你好，stub 回复");
        } finally {
            AgentGraphFactory.clearChatModel();
        }
    }

    @Test
    @DisplayName("用例3: stub ChatModel 抛异常时 invoke() 实际抛出异常，根因为节点异常")
    void invoke_withThrowingChatModel_shouldThrow() throws Exception {
        CompiledGraph<AgentState> graph = new AgentGraphFactory().buildGraph();
        ChatModel throwing = new ThrowingChatModel();

        AgentGraphFactory.bindChatModel(throwing);
        try {
            Throwable thrown = catchThrowable(() ->
                    graph.invoke(Map.of("input", "hi", "chatModel", throwing)));

            // 实测行为：invoke() 抛异常（CompletionException 包装链），不会返回空 Optional
            assertThat(thrown).isNotNull();
            Throwable root = thrown;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            assertThat(root)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("node exploded");
        } finally {
            AgentGraphFactory.clearChatModel();
        }
    }

    // ==================== 用例 4-6：M07 Step4 F04 历史消息注入 ====================

    @Test
    @DisplayName("用例4: 绑定历史消息后 invoke，callModel 收到的 Prompt 含历史 + 新 UserMessage（instructions size = 历史数 + 1）")
    void invoke_withHistoryBound_shouldIncludeHistoryInPrompt() throws Exception {
        CompiledGraph<AgentState> graph = new AgentGraphFactory().buildGraph();
        CapturingChatModel stub = new CapturingChatModel("多轮回复");
        List<Message> history = List.of(
                new UserMessage("第一轮问题"),
                new AssistantMessage("第一轮回答"));

        AgentGraphFactory.bindChatModel(stub);
        AgentGraphFactory.bindHistoryMessages(history);
        try {
            Optional<AgentState> result = graph.invoke(Map.of("input", "第二轮问题", "chatModel", stub));

            assertThat(result).isPresent();
            assertThat(stub.capturedPrompt).isNotNull();
            assertThat(stub.capturedPrompt.getInstructions()).hasSize(3);
            // 历史在前、新 UserMessage 在尾部（顺序 = DB msg_order 升序加载顺序）
            assertThat(stub.capturedPrompt.getInstructions().get(0).getText()).isEqualTo("第一轮问题");
            assertThat(stub.capturedPrompt.getInstructions().get(1).getText()).isEqualTo("第一轮回答");
            assertThat(stub.capturedPrompt.getInstructions().get(2))
                    .isInstanceOf(UserMessage.class)
                    .extracting(Message::getText).isEqualTo("第二轮问题");
            // 无工具 → options 为 null（与 Step2/3 兼容）
            assertThat(stub.capturedPrompt.getOptions()).isNull();
        } finally {
            AgentGraphFactory.clearChatModel();
            AgentGraphFactory.clearHistoryMessages();
        }
    }

    @Test
    @DisplayName("用例5: 未绑定历史（首轮）时行为与 Step2/3 一致——instructions 仅含本轮 UserMessage，不报 NPE")
    void invoke_withoutHistoryBound_shouldFallBackToSingleMessage() throws Exception {
        CompiledGraph<AgentState> graph = new AgentGraphFactory().buildGraph();
        CapturingChatModel stub = new CapturingChatModel("首轮回复");

        AgentGraphFactory.bindChatModel(stub);
        try {
            Optional<AgentState> result = graph.invoke(Map.of("input", "hello", "chatModel", stub));

            assertThat(result).isPresent();
            assertThat(stub.capturedPrompt.getInstructions()).hasSize(1);
            assertThat(stub.capturedPrompt.getInstructions().get(0).getText()).isEqualTo("hello");
            assertThat(stub.capturedPrompt.getOptions()).isNull();
        } finally {
            AgentGraphFactory.clearChatModel();
        }
    }

    @Test
    @DisplayName("用例6: 历史 + 工具同时绑定时，Prompt 携带 ToolCallingChatOptions 且 instructions 含历史")
    void invoke_withHistoryAndTools_shouldCarryOptionsAndHistory() throws Exception {
        CompiledGraph<AgentState> graph = new AgentGraphFactory().buildGraph();
        CapturingChatModel stub = new CapturingChatModel("工具回复");
        List<Message> history = List.of(new UserMessage("旧问题"), new AssistantMessage("旧回答"));
        ToolCallback tool = FunctionToolCallback.builder("echo_tool", (String s) -> "echo:" + s)
                .description("回声工具")
                .inputType(String.class)
                .build();

        AgentGraphFactory.bindChatModel(stub);
        AgentGraphFactory.bindHistoryMessages(history);
        AgentGraphFactory.bindTools(List.of(tool));
        try {
            Optional<AgentState> result = graph.invoke(Map.of("input", "新问题", "chatModel", stub));

            assertThat(result).isPresent();
            assertThat(stub.capturedPrompt.getInstructions()).hasSize(3);
            assertThat(stub.capturedPrompt.getOptions()).isInstanceOf(ToolCallingChatOptions.class);
            assertThat(((ToolCallingChatOptions) stub.capturedPrompt.getOptions()).getToolCallbacks())
                    .extracting(cb -> cb.getToolDefinition().name())
                    .contains("echo_tool");
        } finally {
            AgentGraphFactory.clearChatModel();
            AgentGraphFactory.clearHistoryMessages();
            AgentGraphFactory.clearTools();
        }
    }

    // ==================== 测试 ChatModel 桩 ====================

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

    /** 固定回复的 ChatModel 桩（ChatModel 接口仅 call(Prompt) 为抽象方法，其余均为 default） */
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
            throw new IllegalStateException("node exploded");
        }
    }
}
