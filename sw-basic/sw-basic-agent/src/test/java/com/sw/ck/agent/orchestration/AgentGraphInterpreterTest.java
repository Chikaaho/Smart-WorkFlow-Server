package com.sw.ck.agent.orchestration;

import com.sw.ck.agent.dto.graph.GraphElement;
import com.sw.ck.agent.dto.graph.ProcessGraph;
import com.sw.ck.agent.entity.AgentModelConfig;
import com.sw.ck.common.crypto.AesGcmCipher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AgentGraphInterpreter} 测试（M07-F02 Step8 §7，纯 Java 单测）。
 * <p>
 * mock {@link ChatModelFactory}/{@link AgentToolCallbackFactory}，不起 Spring 上下文；
 * {@link AesGcmCipher} 为 final 类不 mock，用真实实例（测试密钥）验证解密→build 全链路。
 * </p>
 */
@DisplayName("图解释执行引擎测试（纯 Java）")
class AgentGraphInterpreterTest {

    /** 测试 AES 密钥（32 字节 "0123456789abcdef0123456789abcdef" 的 Base64） */
    private static final String TEST_CIPHER_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private final AesGcmCipher cipher = new AesGcmCipher(TEST_CIPHER_KEY);
    private final ChatModelFactory chatModelFactory = mock(ChatModelFactory.class);
    private final AgentToolCallbackFactory toolCallbackFactory = mock(AgentToolCallbackFactory.class);

    // ==================== 用例 1：LLM 单跳覆盖 ====================

    @Test
    @DisplayName("用例1: LLM 节点单跳执行 — 解密 Key→build→以当前文本为 UserMessage 调用→输出覆盖累积文本")
    void llmNode_shouldOverwriteTextWithModelOutput() {
        AgentModelConfig config = modelConfig(1L, "sk-llm-1");
        when(chatModelFactory.build(config, "sk-llm-1")).thenReturn(new StubChatModel("模型回复-1"));

        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_llm", "LLM", Map.of("agentModelConfigId", 1L)),
                node("node_end", "END", Map.of()),
                edge("e1", "node_start", "node_llm", Map.of()),
                edge("e2", "node_llm", "node_end", Map.of()));

        String output = interpreter(Map.of(1L, config), 10).run(graph, "原始输入");

        // 输出被模型回复整体覆盖；模型收到的 UserMessage 文本 = 累积文本（input）
        assertThat(output).isEqualTo("模型回复-1");
        verify(chatModelFactory).build(config, "sk-llm-1");
    }

    // ==================== 用例 2：TOOL 单跳覆盖 ====================

    @Test
    @DisplayName("用例2: TOOL 节点单跳执行 — 按 toolName 精确匹配白名单单个 ToolCallback → 调用 → 覆盖累积文本")
    void toolNode_shouldOverwriteTextWithToolResult() {
        ToolCallback echo = FunctionToolCallback.builder("echo_tool", (String s) -> "echo:" + s)
                .description("回声工具")
                .inputType(String.class)
                .build();
        when(toolCallbackFactory.buildToolCallbacks(any())).thenReturn(List.of(echo));

        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_tool", "TOOL", Map.of("toolName", "echo_tool")),
                node("node_end", "END", Map.of()),
                edge("e1", "node_start", "node_tool", Map.of()),
                edge("e2", "node_tool", "node_end", Map.of()));

        String output = interpreter(Map.of(), 10).run(graph, "你好");

        assertThat(output).isEqualTo("echo:你好");
    }

    // ==================== 用例 3：条件分支命中关键词边 ====================

    @Test
    @DisplayName("用例3: CONDITION 命中关键词边 — 按 elements 出现顺序取第一个命中（文本同时含两个关键词时走先出现的边）")
    void condition_shouldTakeFirstKeywordEdgeInElementsOrder() {
        AgentModelConfig configA = modelConfig(1L, "sk-a");
        AgentModelConfig configB = modelConfig(2L, "sk-b");
        when(chatModelFactory.build(configA, "sk-a")).thenReturn(new StubChatModel("A路输出"));
        when(chatModelFactory.build(configB, "sk-b")).thenReturn(new StubChatModel("B路输出"));

        // 注意 elements 顺序：keyword=发货 的边在 keyword=退款 的边之前（原始顺序即优先级）
        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_cond", "CONDITION", Map.of()),
                node("node_llm_a", "LLM", Map.of("agentModelConfigId", 1L)),
                node("node_llm_b", "LLM", Map.of("agentModelConfigId", 2L)),
                node("node_end", "END", Map.of()),
                edge("e1", "node_start", "node_cond", Map.of()),
                edge("e_ship", "node_cond", "node_llm_b", Map.of("keyword", "发货")),
                edge("e_refund", "node_cond", "node_llm_a", Map.of("keyword", "退款")),
                edge("e3", "node_llm_a", "node_end", Map.of()),
                edge("e4", "node_llm_b", "node_end", Map.of()));

        // 文本同时命中两个关键词 → 取 elements 中先出现的"发货"边 → B 路
        String output = interpreter(Map.of(1L, configA, 2L, configB), 20).run(graph, "用户要求退款并发货");
        assertThat(output).isEqualTo("B路输出");
    }

    // ==================== 用例 4：条件分支未命中 → 走默认边 ====================

    @Test
    @DisplayName("用例4: CONDITION 未命中任何关键词 → 走唯一无 keyword 的默认边")
    void condition_shouldTakeDefaultEdgeWhenNoKeywordMatches() {
        AgentModelConfig configA = modelConfig(1L, "sk-a");
        AgentModelConfig configB = modelConfig(2L, "sk-b");
        when(chatModelFactory.build(configA, "sk-a")).thenReturn(new StubChatModel("关键词路"));
        when(chatModelFactory.build(configB, "sk-b")).thenReturn(new StubChatModel("默认路"));

        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_cond", "CONDITION", Map.of()),
                node("node_llm_a", "LLM", Map.of("agentModelConfigId", 1L)),
                node("node_llm_b", "LLM", Map.of("agentModelConfigId", 2L)),
                node("node_end", "END", Map.of()),
                edge("e1", "node_start", "node_cond", Map.of()),
                edge("e_key", "node_cond", "node_llm_a", Map.of("keyword", "退款")),
                edge("e_default", "node_cond", "node_llm_b", Map.of()),   // 无 keyword = 默认边
                edge("e3", "node_llm_a", "node_end", Map.of()),
                edge("e4", "node_llm_b", "node_end", Map.of()));

        String output = interpreter(Map.of(1L, configA, 2L, configB), 20)
                .run(graph, "没有命中任何关键词的文本");
        assertThat(output).isEqualTo("默认路");
    }

    // ==================== 用例 5：无匹配且无默认边 → 运行时错误 ====================

    @Test
    @DisplayName("用例5: CONDITION 无关键词命中且无默认边 → 抛 GraphExecutionException（图设计缺陷不静默吞掉）")
    void condition_noMatchAndNoDefault_shouldThrow() {
        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_cond", "CONDITION", Map.of()),
                node("node_llm", "LLM", Map.of("agentModelConfigId", 1L)),
                node("node_end", "END", Map.of()),
                edge("e1", "node_start", "node_cond", Map.of()),
                edge("e_key", "node_cond", "node_llm", Map.of("keyword", "退款")),
                edge("e2", "node_llm", "node_end", Map.of()));

        assertThatThrownBy(() -> interpreter(Map.of(), 10).run(graph, "无关文本"))
                .isInstanceOf(AgentGraphInterpreter.GraphExecutionException.class)
                .hasMessageContaining("条件分支无匹配且无默认边");
    }

    // ==================== 用例 6：环 → 步数超限终止 ====================

    @Test
    @DisplayName("用例6: 图存在环（LLM 自环）→ 步数超限抛 GraphExecutionException，不无限执行")
    void cycle_shouldStopAtStepLimit() {
        AgentModelConfig config = modelConfig(1L, "sk-loop");
        when(chatModelFactory.build(config, "sk-loop")).thenReturn(new StubChatModel("循环输出"));

        // 自环：LLM 节点出边指回自身；节点数=2（START+LLM）→ maxSteps=4
        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_llm", "LLM", Map.of("agentModelConfigId", 1L)),
                edge("e1", "node_start", "node_llm", Map.of()),
                edge("e_loop", "node_llm", "node_llm", Map.of()));

        assertThatThrownBy(() -> interpreter(Map.of(1L, config), 4).run(graph, "进来就出不去"))
                .isInstanceOf(AgentGraphInterpreter.GraphExecutionException.class)
                .hasMessageContaining("执行步数超限");
    }

    // ==================== 用例 7：START→LLM→LLM→END 顺序链路 ====================

    @Test
    @DisplayName("用例7: START→LLM→LLM→END 顺序链路 — 逐节点单跳、输出整体覆盖（第二跳收到第一跳输出）")
    void sequentialChain_shouldExecuteInOrderWithOverwrite() {
        AgentModelConfig config1 = modelConfig(1L, "sk-1");
        AgentModelConfig config2 = modelConfig(2L, "sk-2");
        CapturingChatModel stub1 = new CapturingChatModel("第一跳输出");
        CapturingChatModel stub2 = new CapturingChatModel("第二跳输出");
        when(chatModelFactory.build(config1, "sk-1")).thenReturn(stub1);
        when(chatModelFactory.build(config2, "sk-2")).thenReturn(stub2);

        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_llm_1", "LLM", Map.of("agentModelConfigId", 1L)),
                node("node_llm_2", "LLM", Map.of("agentModelConfigId", 2L)),
                node("node_end", "END", Map.of()),
                edge("e1", "node_start", "node_llm_1", Map.of()),
                edge("e2", "node_llm_1", "node_llm_2", Map.of()),
                edge("e3", "node_llm_2", "node_end", Map.of()));

        String output = interpreter(Map.of(1L, config1, 2L, config2), 10).run(graph, "初始文本");

        assertThat(output).isEqualTo("第二跳输出");
        // 第一跳收到初始文本；第二跳收到第一跳的输出（整体覆盖语义）
        assertThat(stub1.capturedPrompt.getInstructions().get(0).getText()).isEqualTo("初始文本");
        assertThat(stub2.capturedPrompt.getInstructions().get(0).getText()).isEqualTo("第一跳输出");
    }

    // ==================== 内部辅助 ====================

    private AgentGraphInterpreter interpreter(Map<Long, AgentModelConfig> modelConfigs, int maxSteps) {
        return new AgentGraphInterpreter(chatModelFactory, toolCallbackFactory, modelConfigs, cipher, null, maxSteps);
    }

    /** 模型配置 POJO（apiKeyCipher 用真实 AES 加密，验证解密→build 全链路） */
    private AgentModelConfig modelConfig(Long id, String plainKey) {
        AgentModelConfig config = new AgentModelConfig();
        config.setId(id);
        config.setProtocolType("openai");
        config.setBaseUrl("http://localhost:9999/v1");
        config.setModelName("stub-model");
        config.setApiKeyCipher(cipher.encrypt(plainKey));
        return config;
    }

    private ProcessGraph graphOf(GraphElement... elements) {
        ProcessGraph graph = new ProcessGraph();
        graph.setGraphKey("test_key");
        graph.setName("测试图");
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

    // ==================== ChatModel 桩 ====================

    /** 记录收到的 Prompt 的 ChatModel 桩（ChatModel 接口仅 call(Prompt) 为抽象方法） */
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
}
