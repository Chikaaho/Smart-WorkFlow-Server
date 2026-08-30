package com.sw.ck.agent.orchestration;

import com.sw.ck.agent.dto.graph.GraphElement;
import com.sw.ck.agent.dto.graph.ProcessGraph;
import com.sw.ck.agent.entity.AgentModelConfig;
import com.sw.ck.common.crypto.AesGcmCipher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    // ==================== 用例 8：命名变量写入 + END 指定输出变量（Step10 多变量） ====================

    @Test
    @DisplayName("用例8: LLM outputVar 写命名变量 + END inputVar 读回 — 命名变量写入不污染默认变量")
    void llmNode_shouldWriteNamedVariableAndEndReadsIt() {
        AgentModelConfig config1 = modelConfig(1L, "sk-1");
        AgentModelConfig config2 = modelConfig(2L, "sk-2");
        when(chatModelFactory.build(config1, "sk-1")).thenReturn(new StubChatModel("摘要输出"));
        CapturingChatModel stub2 = new CapturingChatModel("覆盖默认变量");
        when(chatModelFactory.build(config2, "sk-2")).thenReturn(stub2);

        // LLM1 写命名变量 summary（默认变量保持初始 input 不变）；LLM2 无变量键写默认
        // 变量；END inputVar=summary 取最终输出
        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_llm_1", "LLM", Map.of("agentModelConfigId", 1L, "outputVar", "summary")),
                node("node_llm_2", "LLM", Map.of("agentModelConfigId", 2L)),
                node("node_end", "END", Map.of("inputVar", "summary")),
                edge("e1", "node_start", "node_llm_1", Map.of()),
                edge("e2", "node_llm_1", "node_llm_2", Map.of()),
                edge("e3", "node_llm_2", "node_end", Map.of()));

        String output = interpreter(Map.of(1L, config1, 2L, config2), 10).run(graph, "初始输入");

        // 最终输出来自 summary 变量（LLM1 输出）；LLM2 收到的仍是默认变量（初始 input，
        // 证明命名变量写入未覆盖默认变量——多变量互不污染）
        assertThat(output).isEqualTo("摘要输出");
        assertThat(stub2.capturedPrompt.getInstructions().get(0).getText()).isEqualTo("初始输入");
    }

    // ==================== 用例 9：inputVar + outputVar 链式传递（Step10 多变量） ====================

    @Test
    @DisplayName("用例9: LLM inputVar 读命名变量 + outputVar 写另一命名变量 — 变量间链式传递")
    void llmNode_shouldReadFromInputVarAndWriteToOutputVar() {
        AgentModelConfig config1 = modelConfig(1L, "sk-1");
        AgentModelConfig config2 = modelConfig(2L, "sk-2");
        when(chatModelFactory.build(config1, "sk-1")).thenReturn(new StubChatModel("中间结果"));
        CapturingChatModel stub2 = new CapturingChatModel("最终结果");
        when(chatModelFactory.build(config2, "sk-2")).thenReturn(stub2);

        // LLM1 写 raw；LLM2 从 raw 读（UserMessage = raw 值）、写 final；END 从 final 读
        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_llm_1", "LLM", Map.of("agentModelConfigId", 1L, "outputVar", "raw")),
                node("node_llm_2", "LLM", Map.of("agentModelConfigId", 2L, "inputVar", "raw", "outputVar", "final")),
                node("node_end", "END", Map.of("inputVar", "final")),
                edge("e1", "node_start", "node_llm_1", Map.of()),
                edge("e2", "node_llm_1", "node_llm_2", Map.of()),
                edge("e3", "node_llm_2", "node_end", Map.of()));

        String output = interpreter(Map.of(1L, config1, 2L, config2), 10).run(graph, "初始输入");

        assertThat(output).isEqualTo("最终结果");
        // 第二跳收到的是 raw 变量值（LLM1 输出），不是默认变量（初始 input）
        assertThat(stub2.capturedPrompt.getInstructions().get(0).getText()).isEqualTo("中间结果");
    }

    // ==================== 用例 10：未定义变量引用 → 运行时错误（Step10 多变量） ====================

    @Test
    @DisplayName("用例10: LLM inputVar 引用从未写入的变量 → 抛 GraphExecutionException（运行时错误，不做静态校验）")
    void llmNode_shouldThrowOnUndefinedVariable() {
        AgentModelConfig config = modelConfig(1L, "sk-1");
        when(chatModelFactory.build(config, "sk-1")).thenReturn(new StubChatModel("不应到达"));

        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_llm", "LLM", Map.of("agentModelConfigId", 1L, "inputVar", "notExists")),
                node("node_end", "END", Map.of()),
                edge("e1", "node_start", "node_llm", Map.of()),
                edge("e2", "node_llm", "node_end", Map.of()));

        assertThatThrownBy(() -> interpreter(Map.of(1L, config), 10).run(graph, "文本"))
                .isInstanceOf(AgentGraphInterpreter.GraphExecutionException.class)
                .hasMessageContaining("引用了未定义的变量: notExists");
    }

    // ==================== 用例 11：CONDITION 基于命名变量匹配（Step10 多变量） ====================

    @Test
    @DisplayName("用例11: CONDITION inputVar 指定匹配变量 — 默认变量不含关键词也正确分支")
    void condition_shouldMatchAgainstNamedVariable() {
        AgentModelConfig config1 = modelConfig(1L, "sk-1");
        AgentModelConfig configOk = modelConfig(2L, "sk-ok");
        AgentModelConfig configFail = modelConfig(3L, "sk-fail");
        when(chatModelFactory.build(config1, "sk-1")).thenReturn(new StubChatModel("成功通过"));
        when(chatModelFactory.build(configOk, "sk-ok")).thenReturn(new StubChatModel("成功路"));
        when(chatModelFactory.build(configFail, "sk-fail")).thenReturn(new StubChatModel("失败路"));

        // LLM1 输出写入 judge 变量（内容含"成功"）；默认变量（初始 input）不含关键词；
        // CONDITION inputVar=judge 基于 judge 匹配 → 走成功边
        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_llm_1", "LLM", Map.of("agentModelConfigId", 1L, "outputVar", "judge")),
                node("node_cond", "CONDITION", Map.of("inputVar", "judge")),
                node("node_llm_ok", "LLM", Map.of("agentModelConfigId", 2L)),
                node("node_llm_fail", "LLM", Map.of("agentModelConfigId", 3L)),
                node("node_end", "END", Map.of()),
                edge("e1", "node_start", "node_llm_1", Map.of()),
                edge("e2", "node_llm_1", "node_cond", Map.of()),
                edge("e_ok", "node_cond", "node_llm_ok", Map.of("keyword", "成功")),
                edge("e_fail", "node_cond", "node_llm_fail", Map.of("keyword", "失败")),
                edge("e3", "node_llm_ok", "node_end", Map.of()),
                edge("e4", "node_llm_fail", "node_end", Map.of()));

        String output = interpreter(Map.of(1L, config1, 2L, configOk, 3L, configFail), 20)
                .run(graph, "无关键词文本");

        // 默认变量不含"成功/失败"，若 CONDITION 误用默认变量会走失败边——此处走成功边
        // 证明匹配基于 judge 变量
        assertThat(output).isEqualTo("成功路");
    }

    // ==================== 用例 12：零迁移兼容 — 全默认变量链路与 Step8 语义一致 ====================

    @Test
    @DisplayName("用例12: 旧图（无变量名字段）— LLM 覆盖默认变量后 CONDITION 基于新值匹配（零迁移）")
    void legacyGraph_shouldKeepSingleTextSemantics() {
        AgentModelConfig config1 = modelConfig(1L, "sk-1");
        AgentModelConfig config2 = modelConfig(2L, "sk-2");
        when(chatModelFactory.build(config1, "sk-1")).thenReturn(new StubChatModel("发货请求处理中"));
        when(chatModelFactory.build(config2, "sk-2")).thenReturn(new StubChatModel("发货处理结果"));

        // 全图无任何变量名字段（旧图形态）：LLM 覆盖默认变量 → CONDITION 基于覆盖后的
        // 默认变量匹配（Step8 单文本语义）
        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_llm_1", "LLM", Map.of("agentModelConfigId", 1L)),
                node("node_cond", "CONDITION", Map.of()),
                node("node_llm_2", "LLM", Map.of("agentModelConfigId", 2L)),
                node("node_end", "END", Map.of()),
                edge("e1", "node_start", "node_llm_1", Map.of()),
                edge("e2", "node_llm_1", "node_cond", Map.of()),
                edge("e_ship", "node_cond", "node_llm_2", Map.of("keyword", "发货")),
                edge("e3", "node_llm_2", "node_end", Map.of()));

        String output = interpreter(Map.of(1L, config1, 2L, config2), 10).run(graph, "初始文本");

        assertThat(output).isEqualTo("发货处理结果");
    }

    // ==================== 用例 13：循环正常退出（Step11 LOOP） ====================

    @Test
    @DisplayName("用例13: LOOP 循环正常退出 — 第三轮 CONDITION 命中退出关键词走 END，LLM 恰好调用 3 次（maxIterations=3 未超限）")
    void loop_shouldExitNormallyWhenConditionHitsExitKeyword() {
        AgentModelConfig config = modelConfig(1L, "sk-loop-exit");
        // 有状态桩：前两轮输出不含退出关键词（默认边回 LOOP），第三轮输出"退出"命中关键词走 END
        when(chatModelFactory.build(config, "sk-loop-exit"))
                .thenReturn(new SequencedChatModel("结果1", "结果2", "退出"));

        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_loop", "LOOP", Map.of("maxIterations", 3)),
                node("node_llm", "LLM", Map.of("agentModelConfigId", 1L)),
                node("node_cond", "CONDITION", Map.of()),
                node("node_end", "END", Map.of()),
                edge("e1", "node_start", "node_loop", Map.of()),
                edge("e2", "node_loop", "node_llm", Map.of()),
                edge("e3", "node_llm", "node_cond", Map.of()),
                edge("e_exit", "node_cond", "node_end", Map.of("keyword", "退出")),
                edge("e_back", "node_cond", "node_loop", Map.of()));   // 无 keyword = 默认边回 LOOP

        String output = interpreter(Map.of(1L, config), 30).run(graph, "开始循环");

        // 输出 = 第三轮 LLM 输出（END 读默认变量）；恰好 3 次迭代（少迭代或多迭代该断言失败）
        assertThat(output).isEqualTo("退出");
        verify(chatModelFactory, times(3)).build(config, "sk-loop-exit");
    }

    // ==================== 用例 14：循环迭代超限（Step11 LOOP） ====================

    @Test
    @DisplayName("用例14: LOOP 迭代超限 — 第 3 次到达 LOOP（maxIterations=2）抛 循环迭代次数超限，LLM 恰好执行 2 次")
    void loop_shouldThrowWhenIterationExceedsMax() {
        AgentModelConfig config = modelConfig(1L, "sk-loop-over");
        when(chatModelFactory.build(config, "sk-loop-over")).thenReturn(new StubChatModel("循环体"));

        // LOOP→LLM→(回边直回 LOOP)，无 CONDITION：循环永不退出，靠 LOOP 迭代上限拦截
        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_loop", "LOOP", Map.of("maxIterations", 2)),
                node("node_llm", "LLM", Map.of("agentModelConfigId", 1L)),
                edge("e1", "node_start", "node_loop", Map.of()),
                edge("e2", "node_loop", "node_llm", Map.of()),
                edge("e3", "node_llm", "node_loop", Map.of()));

        assertThatThrownBy(() -> interpreter(Map.of(1L, config), 50).run(graph, "进循环"))
                .isInstanceOf(AgentGraphInterpreter.GraphExecutionException.class)
                .hasMessageContaining("循环迭代次数超限: node_loop");
        // 第 1、2 次到达 LOOP 放行（LLM 各执行一次），第 3 次到达抛错
        verify(chatModelFactory, times(2)).build(config, "sk-loop-over");
    }

    // ==================== 用例 15：FORK→JOIN 两分支全执行（Step11 并行） ====================

    @Test
    @DisplayName("用例15: FORK→JOIN 两分支全执行 — LLM/TOOL 各执行 1 次，JOIN 汇合后 v2 变量保留可读（后验断言）")
    void forkJoin_shouldExecuteAllBranchesAndMerge() {
        AgentModelConfig configB1 = modelConfig(1L, "sk-b1");
        AgentModelConfig configObs = modelConfig(2L, "sk-obs");
        when(chatModelFactory.build(configB1, "sk-b1")).thenReturn(new StubChatModel("分支1输出"));
        CapturingChatModel obs = new CapturingChatModel("观察输出");
        when(chatModelFactory.build(configObs, "sk-obs")).thenReturn(obs);
        ToolCallback echo = FunctionToolCallback.builder("echo_tool", (String s) -> "分支2输出")
                .description("回声工具")
                .inputType(String.class)
                .build();
        when(toolCallbackFactory.buildToolCallbacks(any())).thenReturn(List.of(echo));

        // B1: LLM 写 v1；B2: TOOL 写 v2；JOIN 后置观察节点从 v2 读（证明 v2 写入保留）
        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_fork", "FORK", Map.of()),
                node("node_llm_b1", "LLM", Map.of("agentModelConfigId", 1L, "outputVar", "v1")),
                node("node_tool_b2", "TOOL", Map.of("toolName", "echo_tool", "outputVar", "v2")),
                node("node_join", "JOIN", Map.of()),
                node("node_obs", "LLM", Map.of("agentModelConfigId", 2L, "inputVar", "v2", "outputVar", "v2")),
                node("node_end", "END", Map.of("inputVar", "v1")),
                edge("e1", "node_start", "node_fork", Map.of()),
                edge("e2", "node_fork", "node_llm_b1", Map.of()),
                edge("e3", "node_fork", "node_tool_b2", Map.of()),
                edge("e4", "node_llm_b1", "node_join", Map.of()),
                edge("e5", "node_tool_b2", "node_join", Map.of()),
                edge("e6", "node_join", "node_obs", Map.of()),
                edge("e7", "node_obs", "node_end", Map.of()));

        String output = interpreter(Map.of(1L, configB1, 2L, configObs), 30).run(graph, "入参");

        // END 读到 v1（B1 分支输出）；JOIN 后观察节点从 v2 读到 B2 分支输出（v2 保留 =
        // 两分支全执行并成功汇合）；两个 LLM 各恰好执行 1 次
        assertThat(output).isEqualTo("分支1输出");
        assertThat(obs.capturedPrompt.getInstructions().get(0).getText()).isEqualTo("分支2输出");
        verify(chatModelFactory).build(configB1, "sk-b1");
        verify(chatModelFactory).build(configObs, "sk-obs");
    }

    // ==================== 用例 16：并行同变量后写覆盖（Step11 用户决策） ====================

    @Test
    @DisplayName("用例16: 并行分支同变量后写覆盖 — B1 写 v=A、B2 写 v=B（出边顺序确定），END 读到后推进分支值 B")
    void forkJoin_sameVariable_shouldLastWriteWin() {
        AgentModelConfig config = modelConfig(1L, "sk-b1");
        when(chatModelFactory.build(config, "sk-b1")).thenReturn(new StubChatModel("A"));
        ToolCallback echo = FunctionToolCallback.builder("echo_tool", (String s) -> "B")
                .description("回声工具")
                .inputType(String.class)
                .build();
        when(toolCallbackFactory.buildToolCallbacks(any())).thenReturn(List.of(echo));

        // 出边顺序：B1(LLM 写 v=A) 先于 B2(TOOL 写 v=B)；FIFO 交替推进 = B1 先写、B2 后写覆盖
        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_fork", "FORK", Map.of()),
                node("node_llm_b1", "LLM", Map.of("agentModelConfigId", 1L, "outputVar", "v")),
                node("node_tool_b2", "TOOL", Map.of("toolName", "echo_tool", "outputVar", "v")),
                node("node_join", "JOIN", Map.of()),
                node("node_end", "END", Map.of("inputVar", "v")),
                edge("e1", "node_start", "node_fork", Map.of()),
                edge("e2", "node_fork", "node_llm_b1", Map.of()),
                edge("e3", "node_fork", "node_tool_b2", Map.of()),
                edge("e4", "node_llm_b1", "node_join", Map.of()),
                edge("e5", "node_tool_b2", "node_join", Map.of()),
                edge("e6", "node_join", "node_end", Map.of()));

        String output = interpreter(Map.of(1L, config), 20).run(graph, "入参");

        // 用户已决策：并行同变量 = 最后写入覆盖（不拦截不告警）；确定性出边顺序 → 后推进
        // 分支（B2）的 "B" 覆盖先推进分支（B1）的 "A"
        assertThat(output).isEqualTo("B");
    }

    // ==================== 用例 17：JOIN 死锁兜底（Step11 全局步数） ====================

    @Test
    @DisplayName("用例17: JOIN 死锁兜底 — B1 挂起等待、B2 经 CONDITION 回边死循环永不达 JOIN → 全局步数超限抛错")
    void joinDeadlock_shouldStopAtStepLimit() {
        AgentModelConfig config = modelConfig(1L, "sk-loop2");
        when(chatModelFactory.build(config, "sk-loop2")).thenReturn(new StubChatModel("死循环"));

        // JOIN 静态入边 2 条：B1（FORK→JOIN 直达）与 B2（CONDITION 关键词边 →JOIN）。
        // B1 先到达（计数 1 < 2 → 挂起等待）；B2 经 CONDITION 默认边回 LLM 死循环（匹配
        // 文本"入参"永不含关键词 exit → 永不达 JOIN）→ 全局步数超限兜底（无 LOOP 不放大
        // 预算：沿用 2 × 节点数 = 2 × 6 = 12）
        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_fork", "FORK", Map.of()),
                node("node_join", "JOIN", Map.of()),
                node("node_llm_b2", "LLM", Map.of("agentModelConfigId", 1L)),
                node("node_cond", "CONDITION", Map.of()),
                node("node_end", "END", Map.of()),
                edge("e1", "node_start", "node_fork", Map.of()),
                edge("e2", "node_fork", "node_join", Map.of()),
                edge("e3", "node_fork", "node_llm_b2", Map.of()),
                edge("e4", "node_llm_b2", "node_cond", Map.of()),
                edge("e5", "node_cond", "node_join", Map.of("keyword", "exit")),
                edge("e6", "node_cond", "node_llm_b2", Map.of()),   // 默认边回 LLM = 死循环
                edge("e7", "node_join", "node_end", Map.of()));

        assertThatThrownBy(() -> interpreter(Map.of(1L, config), 12).run(graph, "入参"))
                .isInstanceOf(AgentGraphInterpreter.GraphExecutionException.class)
                .hasMessageContaining("执行步数超限");
    }

    // ==================== 用例 18：END 早到终止全部（Step11 并行） ====================

    @Test
    @DisplayName("用例18: END 早到终止全部 — B1 直达 END 即返回，B2 分支不再执行（LLM 零调用）")
    void endEarly_shouldTerminateAllBranches() {
        AgentModelConfig config = modelConfig(1L, "sk-never");
        when(chatModelFactory.build(config, "sk-never")).thenReturn(new StubChatModel("不应执行"));

        // 出边顺序：B1(→END) 先于 B2(→LLM)；END 一经处理立即返回，B2 分支永不执行
        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_fork", "FORK", Map.of()),
                node("node_end", "END", Map.of()),
                node("node_llm_b2", "LLM", Map.of("agentModelConfigId", 1L)),
                edge("e1", "node_start", "node_fork", Map.of()),
                edge("e2", "node_fork", "node_end", Map.of()),
                edge("e3", "node_fork", "node_llm_b2", Map.of()));

        String output = interpreter(Map.of(1L, config), 10).run(graph, "原始输入");

        // END 读默认变量（无任何节点写入）→ 返回入参原值；B2 分支 LLM 从未被 build
        assertThat(output).isEqualTo("原始输入");
        verify(chatModelFactory, never()).build(any(AgentModelConfig.class), anyString());
    }

    // ==================== 用例 19：预算退化回归（Step11 无 LOOP 旧图） ====================

    @Test
    @DisplayName("用例19: 预算公式退化回归 — 旧图（无 LOOP）自环仍由 2×节点数预算兜底（行为与现状一致）")
    void legacyGraphSelfLoop_shouldStillHitStepLimit() {
        AgentModelConfig config = modelConfig(1L, "sk-legacy-loop");
        when(chatModelFactory.build(config, "sk-legacy-loop")).thenReturn(new StubChatModel("循环输出"));

        // 仿用例6：START + LLM 自环，无 LOOP/END → 节点数 2 × 2 = 4 步后超限抛错
        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_llm", "LLM", Map.of("agentModelConfigId", 1L)),
                edge("e1", "node_start", "node_llm", Map.of()),
                edge("e_loop", "node_llm", "node_llm", Map.of()));

        assertThatThrownBy(() -> interpreter(Map.of(1L, config), 4).run(graph, "进来就出不去"))
                .isInstanceOf(AgentGraphInterpreter.GraphExecutionException.class)
                .hasMessageContaining("执行步数超限");
    }

    // ==================== 用例 20：顺序链路轨迹采集（Step12） ====================

    @Test
    @DisplayName("用例20: 顺序链路轨迹 — START→LLM→END 共 3 条记录，nodeSeq 1-3、branchId 全 0、快照含输入与模型输出")
    void sequential_trace_shouldRecordEveryNodeVisit() {
        AgentModelConfig config = modelConfig(1L, "sk-trace");
        when(chatModelFactory.build(config, "sk-trace")).thenReturn(new StubChatModel("轨迹输出"));

        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_llm", "LLM", Map.of("agentModelConfigId", 1L)),
                node("node_end", "END", Map.of()),
                edge("e1", "node_start", "node_llm", Map.of()),
                edge("e2", "node_llm", "node_end", Map.of()));

        AgentGraphInterpreter interpreter = interpreter(Map.of(1L, config), 10);
        String output = interpreter.run(graph, "原始输入");
        List<AgentGraphInterpreter.NodeExecutionTrace> traces = interpreter.getTraces();

        assertThat(output).isEqualTo("轨迹输出");
        assertThat(traces).hasSize(3);
        assertThat(traces.get(0).getNodeSeq()).isEqualTo(1);
        assertThat(traces.get(0).getNodeId()).isEqualTo("node_start");
        assertThat(traces.get(0).getNodeType()).isEqualTo("START");
        assertThat(traces.get(1).getNodeSeq()).isEqualTo(2);
        assertThat(traces.get(1).getNodeId()).isEqualTo("node_llm");
        assertThat(traces.get(1).getNodeType()).isEqualTo("LLM");
        assertThat(traces.get(2).getNodeSeq()).isEqualTo(3);
        assertThat(traces.get(2).getNodeId()).isEqualTo("node_end");
        // 分支标识：非 FORK 路径恒为 "0"
        assertThat(traces).allSatisfy(t -> assertThat(t.getBranchId()).isEqualTo("0"));
        // 节点级耗时非负
        assertThat(traces).allSatisfy(t -> assertThat(t.getNodeLatencyMs()).isNotNegative());
        // 变量快照：START 后仅默认变量（入参原值）；LLM 后默认变量被模型输出覆盖
        assertThat(traces.get(0).getVariableSnapshot()).containsEntry("input", "原始输入");
        assertThat(traces.get(1).getVariableSnapshot()).containsEntry("input", "轨迹输出");
    }

    // ==================== 用例 21：并行分支轨迹标识（Step12） ====================

    @Test
    @DisplayName("用例21: FORK→JOIN 并行轨迹 — 两分支 branchId 分别为 0-0/0-1（按出边顺序），JOIN 后沿用最后到达分支标识")
    void forkJoin_trace_shouldDistinguishBranches() {
        AgentModelConfig configB1 = modelConfig(1L, "sk-b1");
        when(chatModelFactory.build(configB1, "sk-b1")).thenReturn(new StubChatModel("分支1"));
        ToolCallback echo = FunctionToolCallback.builder("echo_tool", (String s) -> "分支2")
                .description("回声工具")
                .inputType(String.class)
                .build();
        when(toolCallbackFactory.buildToolCallbacks(any())).thenReturn(List.of(echo));

        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_fork", "FORK", Map.of()),
                node("node_llm_b1", "LLM", Map.of("agentModelConfigId", 1L, "outputVar", "v1")),
                node("node_tool_b2", "TOOL", Map.of("toolName", "echo_tool", "outputVar", "v2")),
                node("node_join", "JOIN", Map.of()),
                node("node_end", "END", Map.of("inputVar", "v1")),
                edge("e1", "node_start", "node_fork", Map.of()),
                edge("e2", "node_fork", "node_llm_b1", Map.of()),
                edge("e3", "node_fork", "node_tool_b2", Map.of()),
                edge("e4", "node_llm_b1", "node_join", Map.of()),
                edge("e5", "node_tool_b2", "node_join", Map.of()),
                edge("e6", "node_join", "node_end", Map.of()));

        AgentGraphInterpreter interpreter = interpreter(Map.of(1L, configB1), 30);
        String output = interpreter.run(graph, "入参");
        List<AgentGraphInterpreter.NodeExecutionTrace> traces = interpreter.getTraces();

        assertThat(output).isEqualTo("分支1");
        // FIFO 交替推进：START(0) → FORK(0) → B1(0-0) → B2(0-1) → JOIN 两次到达
        // （0-0 挂起留痕、0-1 汇合放行）→ END(0-1，沿用最后到达分支)
        assertThat(traces).hasSize(7);
        assertThat(traces.get(0).getBranchId()).isEqualTo("0");          // START
        assertThat(traces.get(1).getBranchId()).isEqualTo("0");          // FORK
        assertThat(traces.get(2).getBranchId()).isEqualTo("0-0");        // B1（第一条出边）
        assertThat(traces.get(3).getBranchId()).isEqualTo("0-1");        // B2（第二条出边）
        assertThat(traces.get(4).getBranchId()).isEqualTo("0-0");        // JOIN 第一次到达（挂起）
        assertThat(traces.get(5).getBranchId()).isEqualTo("0-1");        // JOIN 第二次到达（汇合放行）
        assertThat(traces.get(6).getBranchId()).isEqualTo("0-1");        // END（沿用最后到达分支）
        // nodeSeq 全局递增且唯一（JOIN 挂起到达同样占一条，分支轨迹完整留痕）
        List<Long> seqs = traces.stream().map(AgentGraphInterpreter.NodeExecutionTrace::getNodeSeq).toList();
        assertThat(seqs).isSorted();
        assertThat(seqs).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L);
    }

    // ==================== 用例 22：循环迭代轨迹（Step12） ====================

    @Test
    @DisplayName("用例22: LOOP 迭代轨迹 — 同节点多次访问 = 多条 nodeSeq 递增记录，branchId 相同，快照反映迭代输出")
    void loop_trace_shouldRecordEveryIteration() {
        AgentModelConfig config = modelConfig(1L, "sk-loop-trace");
        when(chatModelFactory.build(config, "sk-loop-trace"))
                .thenReturn(new SequencedChatModel("结果1", "结果2", "退出"));

        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_loop", "LOOP", Map.of("maxIterations", 3)),
                node("node_llm", "LLM", Map.of("agentModelConfigId", 1L)),
                node("node_cond", "CONDITION", Map.of()),
                node("node_end", "END", Map.of()),
                edge("e1", "node_start", "node_loop", Map.of()),
                edge("e2", "node_loop", "node_llm", Map.of()),
                edge("e3", "node_llm", "node_cond", Map.of()),
                edge("e_exit", "node_cond", "node_end", Map.of("keyword", "退出")),
                edge("e_back", "node_cond", "node_loop", Map.of()));

        AgentGraphInterpreter interpreter = interpreter(Map.of(1L, config), 30);
        String output = interpreter.run(graph, "开始循环");
        List<AgentGraphInterpreter.NodeExecutionTrace> traces = interpreter.getTraces();

        assertThat(output).isEqualTo("退出");
        // 3 轮迭代：LOOP 节点被访问 3 次（含退出轮），LLM 3 次，CONDITION 3 次，+START+END
        List<AgentGraphInterpreter.NodeExecutionTrace> loopVisits = traces.stream()
                .filter(t -> t.getNodeId().equals("node_loop"))
                .toList();
        assertThat(loopVisits).hasSize(3);
        assertThat(loopVisits).allSatisfy(t -> assertThat(t.getBranchId()).isEqualTo("0"));
        // 同分支迭代 = nodeSeq 递增的独立记录（轨迹可区分第几次访问）
        assertThat(loopVisits.get(0).getNodeSeq()).isLessThan(loopVisits.get(1).getNodeSeq());
        assertThat(loopVisits.get(1).getNodeSeq()).isLessThan(loopVisits.get(2).getNodeSeq());
        // 循环体变量的演进在快照中可见（默认变量 = 最新 LLM 输出）
        AgentGraphInterpreter.NodeExecutionTrace lastLlm = traces.stream()
                .filter(t -> t.getNodeType().equals("LLM"))
                .reduce((a, b) -> b).orElseThrow();
        assertThat(lastLlm.getVariableSnapshot()).containsEntry("input", "退出");
    }

    // ==================== 用例 23：失败路径轨迹与错误分类（Step12） ====================

    @Test
    @DisplayName("用例23: 失败路径留痕 — 条件无匹配且无默认边抛错后轨迹仍完整（含失败节点行），异常分类 CONDITION_NO_MATCH")
    void failure_trace_shouldRecordVisitedNodesAndCategory() {
        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_cond", "CONDITION", Map.of()),
                node("node_llm", "LLM", Map.of("agentModelConfigId", 1L)),
                node("node_end", "END", Map.of()),
                edge("e1", "node_start", "node_cond", Map.of()),
                edge("e_key", "node_cond", "node_llm", Map.of("keyword", "退款")),
                edge("e2", "node_llm", "node_end", Map.of()));

        AgentGraphInterpreter interpreter = interpreter(Map.of(), 10);
        assertThatThrownBy(() -> interpreter.run(graph, "无关文本"))
                .isInstanceOf(AgentGraphInterpreter.GraphExecutionException.class)
                .hasMessageContaining("条件分支无匹配且无默认边")
                .satisfies(ex -> assertThat(((AgentGraphInterpreter.GraphExecutionException) ex)
                        .getCategory()).isEqualTo("CONDITION_NO_MATCH"));

        // 失败路径轨迹完整：START、CONDITION（失败节点）均有行，nodeSeq 递增
        List<AgentGraphInterpreter.NodeExecutionTrace> traces = interpreter.getTraces();
        assertThat(traces).hasSize(2);
        assertThat(traces.get(0).getNodeId()).isEqualTo("node_start");
        assertThat(traces.get(1).getNodeId()).isEqualTo("node_cond");
        assertThat(traces.get(1).getNodeLatencyMs()).isNotNegative();
    }

    // ==================== 用例 24：错误分类 — 步数超限（Step12） ====================

    @Test
    @DisplayName("用例24: 错误分类 — 步数超限抛 GraphExecutionException.category = STEP_LIMIT")
    void stepLimit_shouldCarryCategory() {
        AgentModelConfig config = modelConfig(1L, "sk-cat");
        when(chatModelFactory.build(config, "sk-cat")).thenReturn(new StubChatModel("循环输出"));

        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_llm", "LLM", Map.of("agentModelConfigId", 1L)),
                edge("e1", "node_start", "node_llm", Map.of()),
                edge("e_loop", "node_llm", "node_llm", Map.of()));

        assertThatThrownBy(() -> interpreter(Map.of(1L, config), 4).run(graph, "进来就出不去"))
                .isInstanceOf(AgentGraphInterpreter.GraphExecutionException.class)
                .hasMessageContaining("执行步数超限")
                .satisfies(ex -> assertThat(((AgentGraphInterpreter.GraphExecutionException) ex)
                        .getCategory()).isEqualTo("STEP_LIMIT"));
    }

    // ==================== 用例 25：历史图无 Prompt 配置 → 仅 UserMessage（零迁移） ====================

    @Test
    @DisplayName("用例25: 历史图无 systemPrompt/userPromptTemplate → 仅 UserMessage(inputVar 值)，结果与旧行为一致")
    void llmNodeWithoutPromptConfigBackwardCompatible() {
        AgentModelConfig config = modelConfig(1L, "sk-hist");
        CapturingChatModel stub = new CapturingChatModel("历史回复");
        when(chatModelFactory.build(config, "sk-hist")).thenReturn(stub);

        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_llm", "LLM", Map.of("agentModelConfigId", 1L)),
                node("node_end", "END", Map.of()),
                edge("e1", "node_start", "node_llm", Map.of()),
                edge("e2", "node_llm", "node_end", Map.of()));

        String output = interpreter(Map.of(1L, config), 10).run(graph, "历史输入");

        assertThat(output).isEqualTo("历史回复");
        List<Message> msgs = stub.capturedPrompt.getInstructions();
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0).getMessageType()).isEqualTo(MessageType.USER);
        assertThat(msgs.get(0).getText()).isEqualTo("历史输入");
    }

    // ==================== 用例 26：配置 systemPrompt → 消息列表含 SystemMessage + UserMessage ====================

    @Test
    @DisplayName("用例26: 配置 systemPrompt → 消息列表含 SystemMessage + UserMessage（顺序：System 在前，User 在后）")
    void llmNodeWithSystemPromptInjected() {
        AgentModelConfig config = modelConfig(1L, "sk-sys");
        CapturingChatModel stub = new CapturingChatModel("系统回复");
        when(chatModelFactory.build(config, "sk-sys")).thenReturn(stub);

        ProcessGraph graph = graphOf(
                node("node_start", "START", Map.of()),
                node("node_llm", "LLM", Map.of("agentModelConfigId", 1L,
                        "systemPrompt", "You are a helpful assistant.")),
                node("node_end", "END", Map.of()),
                edge("e1", "node_start", "node_llm", Map.of()),
                edge("e2", "node_llm", "node_end", Map.of()));

        String output = interpreter(Map.of(1L, config), 10).run(graph, "你好");

        assertThat(output).isEqualTo("系统回复");
        List<Message> msgs = stub.capturedPrompt.getInstructions();
        assertThat(msgs).hasSize(2);
        assertThat(msgs.get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(msgs.get(0).getText()).isEqualTo("You are a helpful assistant.");
        assertThat(msgs.get(1).getMessageType()).isEqualTo(MessageType.USER);
        assertThat(msgs.get(1).getText()).isEqualTo("你好");
    }

    // ==================== 用例 27：空 systemPrompt → 不注入 SystemMessage ====================

    @Test
    @DisplayName("用例27: systemPrompt=\"\" 或 \"   \" → 仅 UserMessage（空白系统 Prompt 不注入）")
    void llmNodeWithBlankSystemPromptNotInjected() {
        AgentModelConfig config1 = modelConfig(1L, "sk-b1");
        AgentModelConfig config2 = modelConfig(2L, "sk-b2");
        CapturingChatModel stub1 = new CapturingChatModel("空串回复");
        CapturingChatModel stub2 = new CapturingChatModel("空白回复");
        when(chatModelFactory.build(config1, "sk-b1")).thenReturn(stub1);
        when(chatModelFactory.build(config2, "sk-b2")).thenReturn(stub2);

        // 空串
        ProcessGraph graph1 = graphOf(
                node("s", "START", Map.of()),
                node("llm1", "LLM", Map.of("agentModelConfigId", 1L, "systemPrompt", "")),
                node("e", "END", Map.of()),
                edge("x1", "s", "llm1", Map.of()),
                edge("x2", "llm1", "e", Map.of()));
        // 纯空白
        ProcessGraph graph2 = graphOf(
                node("s", "START", Map.of()),
                node("llm2", "LLM", Map.of("agentModelConfigId", 2L, "systemPrompt", "   ")),
                node("e", "END", Map.of()),
                edge("x1", "s", "llm2", Map.of()),
                edge("x2", "llm2", "e", Map.of()));

        interpreter(Map.of(1L, config1), 10).run(graph1, "输入1");
        interpreter(Map.of(2L, config2), 10).run(graph2, "输入2");

        assertThat(stub1.capturedPrompt.getInstructions()).hasSize(1);
        assertThat(stub1.capturedPrompt.getInstructions().get(0).getMessageType()).isEqualTo(MessageType.USER);
        assertThat(stub2.capturedPrompt.getInstructions()).hasSize(1);
        assertThat(stub2.capturedPrompt.getInstructions().get(0).getMessageType()).isEqualTo(MessageType.USER);
    }

    // ==================== 用例 28：userPromptTemplate 单变量插值 ====================

    @Test
    @DisplayName("用例28: userPromptTemplate=\"Hello, {{name}}\" + variables={name=alice} → 用户消息=\"Hello, alice\"")
    void llmNodeWithUserPromptTemplateSingleVar() {
        AgentModelConfig config = modelConfig(1L, "sk-tpl");
        CapturingChatModel stub = new CapturingChatModel("模板回复");
        when(chatModelFactory.build(config, "sk-tpl")).thenReturn(stub);

        // LLM1 写 name 变量；LLM2 用模板引用 {{name}}
        AgentModelConfig configPre = modelConfig(2L, "sk-pre");
        when(chatModelFactory.build(configPre, "sk-pre")).thenReturn(new StubChatModel("alice"));

        ProcessGraph graph = graphOf(
                node("s", "START", Map.of()),
                node("llm1", "LLM", Map.of("agentModelConfigId", 2L, "outputVar", "name")),
                node("llm2", "LLM", Map.of("agentModelConfigId", 1L,
                        "userPromptTemplate", "Hello, {{name}}")),
                node("e", "END", Map.of()),
                edge("x1", "s", "llm1", Map.of()),
                edge("x2", "llm1", "llm2", Map.of()),
                edge("x3", "llm2", "e", Map.of()));

        String output = interpreter(Map.of(1L, config, 2L, configPre), 10).run(graph, "ignored");

        assertThat(output).isEqualTo("模板回复");
        List<Message> msgs = stub.capturedPrompt.getInstructions();
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0).getMessageType()).isEqualTo(MessageType.USER);
        assertThat(msgs.get(0).getText()).isEqualTo("Hello, alice");
    }

    // ==================== 用例 29：userPromptTemplate 多变量插值 ====================

    @Test
    @DisplayName("用例29: userPromptTemplate=\"{{greeting}} {{name}}\" + 两个命名变量 → 正确拼接")
    void llmNodeWithUserPromptTemplateMultipleVars() {
        AgentModelConfig configTpl = modelConfig(1L, "sk-tpl");
        AgentModelConfig configG = modelConfig(2L, "sk-g");
        AgentModelConfig configN = modelConfig(3L, "sk-n");
        CapturingChatModel stub = new CapturingChatModel("多变量回复");
        when(chatModelFactory.build(configTpl, "sk-tpl")).thenReturn(stub);
        when(chatModelFactory.build(configG, "sk-g")).thenReturn(new StubChatModel("Hi"));
        when(chatModelFactory.build(configN, "sk-n")).thenReturn(new StubChatModel("bob"));

        ProcessGraph graph = graphOf(
                node("s", "START", Map.of()),
                node("llm_g", "LLM", Map.of("agentModelConfigId", 2L, "outputVar", "greeting")),
                node("llm_n", "LLM", Map.of("agentModelConfigId", 3L, "outputVar", "name")),
                node("llm_tpl", "LLM", Map.of("agentModelConfigId", 1L,
                        "userPromptTemplate", "{{greeting}} {{name}}")),
                node("e", "END", Map.of()),
                edge("x1", "s", "llm_g", Map.of()),
                edge("x2", "llm_g", "llm_n", Map.of()),
                edge("x3", "llm_n", "llm_tpl", Map.of()),
                edge("x4", "llm_tpl", "e", Map.of()));

        interpreter(Map.of(1L, configTpl, 2L, configG, 3L, configN), 10).run(graph, "ignored");

        assertThat(stub.capturedPrompt.getInstructions().get(0).getText()).isEqualTo("Hi bob");
    }

    // ==================== 用例 30：同一变量出现两次 → 均被替换 ====================

    @Test
    @DisplayName("用例30: userPromptTemplate 同一变量出现两次 → 均被替换为同一值")
    void llmNodeWithUserPromptTemplateRepeatedVar() {
        AgentModelConfig configTpl = modelConfig(1L, "sk-tpl");
        AgentModelConfig configPre = modelConfig(2L, "sk-pre");
        CapturingChatModel stub = new CapturingChatModel("重复回复");
        when(chatModelFactory.build(configTpl, "sk-tpl")).thenReturn(stub);
        when(chatModelFactory.build(configPre, "sk-pre")).thenReturn(new StubChatModel("alice"));

        ProcessGraph graph = graphOf(
                node("s", "START", Map.of()),
                node("llm1", "LLM", Map.of("agentModelConfigId", 2L, "outputVar", "name")),
                node("llm2", "LLM", Map.of("agentModelConfigId", 1L,
                        "userPromptTemplate", "Hi {{name}}, again {{name}}!")),
                node("e", "END", Map.of()),
                edge("x1", "s", "llm1", Map.of()),
                edge("x2", "llm1", "llm2", Map.of()),
                edge("x3", "llm2", "e", Map.of()));

        interpreter(Map.of(1L, configTpl, 2L, configPre), 10).run(graph, "ignored");

        assertThat(stub.capturedPrompt.getInstructions().get(0).getText())
                .isEqualTo("Hi alice, again alice!");
    }

    // ==================== 用例 31：模板与变量含中文 / emoji → 编码正确 ====================

    @Test
    @DisplayName("用例31: 模板含中文、变量值含中文/emoji → 正确替换不破坏编码")
    void llmNodeWithUserPromptTemplateNonAscii() {
        AgentModelConfig configTpl = modelConfig(1L, "sk-tpl");
        AgentModelConfig configPre = modelConfig(2L, "sk-pre");
        CapturingChatModel stub = new CapturingChatModel("非ASCII回复");
        when(chatModelFactory.build(configTpl, "sk-tpl")).thenReturn(stub);
        when(chatModelFactory.build(configPre, "sk-pre")).thenReturn(new StubChatModel("🌟小明"));

        ProcessGraph graph = graphOf(
                node("s", "START", Map.of()),
                node("llm1", "LLM", Map.of("agentModelConfigId", 2L, "outputVar", "name")),
                node("llm2", "LLM", Map.of("agentModelConfigId", 1L,
                        "userPromptTemplate", "你好，{{name}}！欢迎使用系统。")),
                node("e", "END", Map.of()),
                edge("x1", "s", "llm1", Map.of()),
                edge("x2", "llm1", "llm2", Map.of()),
                edge("x3", "llm2", "e", Map.of()));

        interpreter(Map.of(1L, configTpl, 2L, configPre), 10).run(graph, "ignored");

        assertThat(stub.capturedPrompt.getInstructions().get(0).getText())
                .isEqualTo("你好，🌟小明！欢迎使用系统。");
    }

    // ==================== 用例 32：变量值含 {{x}} 不被二次解析（关键：防表达式注入） ====================

    @Test
    @DisplayName("用例32: 变量值本身含 '{{x}}' → 不被二次解析（避免表达式注入）")
    void llmNodeWithTemplateValueContainingBracesNoSecondPass() {
        AgentModelConfig configTpl = modelConfig(1L, "sk-tpl");
        AgentModelConfig configPre = modelConfig(2L, "sk-pre");
        CapturingChatModel stub = new CapturingChatModel("安全回复");
        when(chatModelFactory.build(configTpl, "sk-tpl")).thenReturn(stub);
        // 变量值含 {{x}} 样式（模拟用户输入被写入变量）
        when(chatModelFactory.build(configPre, "sk-pre"))
                .thenReturn(new StubChatModel("{{injected}}"));

        ProcessGraph graph = graphOf(
                node("s", "START", Map.of()),
                node("llm1", "LLM", Map.of("agentModelConfigId", 2L, "outputVar", "payload")),
                node("llm2", "LLM", Map.of("agentModelConfigId", 1L,
                        "userPromptTemplate", "收到: {{payload}}")),
                node("e", "END", Map.of()),
                edge("x1", "s", "llm1", Map.of()),
                edge("x2", "llm1", "llm2", Map.of()),
                edge("x3", "llm2", "e", Map.of()));

        interpreter(Map.of(1L, configTpl, 2L, configPre), 10).run(graph, "ignored");

        // 关键：变量值中的 {{injected}} 不被二次展开，原样出现在用户消息中
        assertThat(stub.capturedPrompt.getInstructions().get(0).getText())
                .isEqualTo("收到: {{injected}}");
    }

    // ==================== 用例 33：模板引用未定义变量 → 抛错，不调用模型 ====================

    @Test
    @DisplayName("用例33: userPromptTemplate 引用未定义变量 → 抛 GraphExecutionException (UNDEFINED_VARIABLE)，不调用模型")
    void llmNodeWithTemplateUndefinedVariableFails() {
        AgentModelConfig config = modelConfig(1L, "sk-tpl");
        // 用 mock ChatModel 验证 call() 从未被调用（模板插值在 call 之前抛错）
        ChatModel mockChatModel = mock(ChatModel.class);
        when(chatModelFactory.build(config, "sk-tpl")).thenReturn(mockChatModel);

        ProcessGraph graph = graphOf(
                node("s", "START", Map.of()),
                node("llm", "LLM", Map.of("agentModelConfigId", 1L,
                        "userPromptTemplate", "Hello, {{missing}}!")),
                node("e", "END", Map.of()),
                edge("x1", "s", "llm", Map.of()),
                edge("x2", "llm", "e", Map.of()));

        assertThatThrownBy(() -> interpreter(Map.of(1L, config), 10).run(graph, "ignored"))
                .isInstanceOf(AgentGraphInterpreter.GraphExecutionException.class)
                .hasMessageContaining("引用了未定义的变量: missing")
                .satisfies(ex -> assertThat(((AgentGraphInterpreter.GraphExecutionException) ex)
                        .getCategory()).isEqualTo("UNDEFINED_VARIABLE"));
        // 模板插值在 chatModel.call() 之前抛错 —— 模型从未被调用
        verify(mockChatModel, never()).call(any(Prompt.class));
    }

    // ==================== 用例 34：空 userPromptTemplate → 退化为 inputVar 原文 ====================

    @Test
    @DisplayName("用例34: userPromptTemplate=\"\" 或 null → 退化为 inputVar 原文（历史行为）")
    void llmNodeWithBlankUserPromptTemplateFallsBackToInputVar() {
        AgentModelConfig config1 = modelConfig(1L, "sk-b1");
        AgentModelConfig config2 = modelConfig(2L, "sk-b2");
        CapturingChatModel stub1 = new CapturingChatModel("空模板回复");
        CapturingChatModel stub2 = new CapturingChatModel("缺键回复");
        when(chatModelFactory.build(config1, "sk-b1")).thenReturn(stub1);
        when(chatModelFactory.build(config2, "sk-b2")).thenReturn(stub2);

        // 空串 userPromptTemplate
        ProcessGraph graph1 = graphOf(
                node("s", "START", Map.of()),
                node("llm1", "LLM", Map.of("agentModelConfigId", 1L, "userPromptTemplate", "")),
                node("e", "END", Map.of()),
                edge("x1", "s", "llm1", Map.of()),
                edge("x2", "llm1", "e", Map.of()));
        // 完全缺 userPromptTemplate（历史图形态）
        ProcessGraph graph2 = graphOf(
                node("s", "START", Map.of()),
                node("llm2", "LLM", Map.of("agentModelConfigId", 2L)),
                node("e", "END", Map.of()),
                edge("x1", "s", "llm2", Map.of()),
                edge("x2", "llm2", "e", Map.of()));

        interpreter(Map.of(1L, config1), 10).run(graph1, "原始输入1");
        interpreter(Map.of(2L, config2), 10).run(graph2, "原始输入2");

        // 两者都退化为 inputVar 原文
        assertThat(stub1.capturedPrompt.getInstructions().get(0).getText()).isEqualTo("原始输入1");
        assertThat(stub2.capturedPrompt.getInstructions().get(0).getText()).isEqualTo("原始输入2");
    }

    // ==================== 用例 35：systemPrompt + userPromptTemplate 组合 ====================

    @Test
    @DisplayName("用例35: 同时配置 systemPrompt + userPromptTemplate → 消息列表：System 在前、User 在后")
    void llmNodeWithTemplateAndSystemPromptCombined() {
        AgentModelConfig configTpl = modelConfig(1L, "sk-tpl");
        AgentModelConfig configPre = modelConfig(2L, "sk-pre");
        CapturingChatModel stub = new CapturingChatModel("组合回复");
        when(chatModelFactory.build(configTpl, "sk-tpl")).thenReturn(stub);
        when(chatModelFactory.build(configPre, "sk-pre")).thenReturn(new StubChatModel("alice"));

        ProcessGraph graph = graphOf(
                node("s", "START", Map.of()),
                node("llm1", "LLM", Map.of("agentModelConfigId", 2L, "outputVar", "name")),
                node("llm2", "LLM", Map.of("agentModelConfigId", 1L,
                        "systemPrompt", "你是一个客服机器人。",
                        "userPromptTemplate", "用户名字: {{name}}")),
                node("e", "END", Map.of()),
                edge("x1", "s", "llm1", Map.of()),
                edge("x2", "llm1", "llm2", Map.of()),
                edge("x3", "llm2", "e", Map.of()));

        interpreter(Map.of(1L, configTpl, 2L, configPre), 10).run(graph, "ignored");

        List<Message> msgs = stub.capturedPrompt.getInstructions();
        assertThat(msgs).hasSize(2);
        assertThat(msgs.get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(msgs.get(0).getText()).isEqualTo("你是一个客服机器人。");
        assertThat(msgs.get(1).getMessageType()).isEqualTo(MessageType.USER);
        assertThat(msgs.get(1).getText()).isEqualTo("用户名字: alice");
    }

    // ==================== 用例 36：模板含非标识符语法 → 原文保留不报错 ====================

    @Test
    @DisplayName("用例36: 模板含 '{{ invalid name }}'（空格）或 '{{123numeric}}'（非标识符开头）→ 不被识别为占位符，原文保留")
    void llmNodeWithTemplateContainingUnknownSyntaxLeftIntact() {
        AgentModelConfig config = modelConfig(1L, "sk-tpl");
        CapturingChatModel stub = new CapturingChatModel("语法保留回复");
        when(chatModelFactory.build(config, "sk-tpl")).thenReturn(stub);

        ProcessGraph graph = graphOf(
                node("s", "START", Map.of()),
                node("llm", "LLM", Map.of("agentModelConfigId", 1L,
                        "userPromptTemplate", "{{ invalid name }} and {{123numeric}} and {{valid}}")),
                node("e", "END", Map.of()),
                edge("x1", "s", "llm", Map.of()),
                edge("x2", "llm", "e", Map.of()));

        // 变量表只有默认变量 input，无 valid 变量 —— 会触发 UNDEFINED_VARIABLE
        // 所以先测不含 valid 的版本
        ProcessGraph graphSafe = graphOf(
                node("s", "START", Map.of()),
                node("llm", "LLM", Map.of("agentModelConfigId", 1L,
                        "userPromptTemplate", "{{ invalid name }} and {{123numeric}} left as-is.")),
                node("e", "END", Map.of()),
                edge("x1", "s", "llm", Map.of()),
                edge("x2", "llm", "e", Map.of()));

        interpreter(Map.of(1L, config), 10).run(graphSafe, "ignored");

        // 非标识符语法的占位符不被识别，原文保留
        assertThat(stub.capturedPrompt.getInstructions().get(0).getText())
                .isEqualTo("{{ invalid name }} and {{123numeric}} left as-is.");
        // 确认 graph（含 {{valid}}）会触发 UNDEFINED_VARIABLE
        assertThatThrownBy(() -> interpreter(Map.of(1L, config), 10).run(graph, "ignored"))
                .isInstanceOf(AgentGraphInterpreter.GraphExecutionException.class)
                .hasMessageContaining("引用了未定义的变量: valid");
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

    /** 按调用顺序返回固定序列回复的 ChatModel 桩（超出序列后重复最后一个，用于循环退出用例） */
    static class SequencedChatModel implements ChatModel {
        private final String[] replies;
        private int index = 0;

        SequencedChatModel(String... replies) {
            this.replies = replies;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            String reply = replies[Math.min(index, replies.length - 1)];
            index++;
            return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
        }
    }
}
