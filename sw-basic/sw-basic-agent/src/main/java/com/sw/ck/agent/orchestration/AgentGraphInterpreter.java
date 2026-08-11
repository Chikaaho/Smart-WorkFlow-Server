package com.sw.ck.agent.orchestration;

import com.sw.ck.agent.dto.graph.GraphElement;
import com.sw.ck.agent.dto.graph.ProcessGraph;
import com.sw.ck.agent.entity.AgentModelConfig;
import com.sw.ck.common.crypto.AesGcmCipher;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.util.json.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 图解释执行引擎（M07-F02 Step8 第一版，纯 Java 无 Spring 注解，可独立单测）。
 * <p>
 * 直接解释 {@link ProcessGraph#getElements()}（Step7 产物）：从唯一 START 节点出发，
 * 按 elements 顺序遍历节点与边，执行 LLM 节点（单跳调用，无工具无历史）/工具节点
 * （按名称精确定位单个 {@link ToolCallback} 直接调用）/条件分支节点（关键词子串匹配
 * 选路），直到 END 节点，返回最终累积文本。
 * </p>
 * <p>
 * <b>执行上下文（本版极简）</b>：单一 {@code String currentText}（初始 = 请求
 * {@code input}），LLM/工具节点输出<b>整体覆盖</b>（非追加、非结构化多变量），
 * END 时 currentText 即为最终 output。多变量执行上下文是后续批次演进方向，本版不做。
 * </p>
 * <p>
 * <b>与 F01 的关系（方案 §2-A）</b>：本类是与 {@link AgentGraphFactory}/LangGraph4j
 * 并存的独立执行路径，互不修改、互不依赖。F01 是"LLM 自主决定是否调工具"的单跳
 * agentic 调用；本类是"用户显式画出的多步顺序，工具何时调、条件怎么分支由图拓扑
 * 决定"的图解释执行——复用更底层的构造块 {@link ChatModelFactory#build} 与
 * {@link AgentToolCallbackFactory#buildToolCallbacks}，但以不同方式调用：
 * LLM 节点按 {@code config.agentModelConfigId} 指向单个配置单跳调用；
 * 工具节点从白名单装载结果中<b>按名称精确定位单个</b> {@link ToolCallback} 调用，
 * 而非像 F01 把全部工具注入 LLM。
 * </p>
 * <p>
 * <b>节点 config 语义（本 Step 定义的执行契约）</b>：LLM 节点
 * {@code config.agentModelConfigId}（Long，必填）；TOOL 节点
 * {@code config.toolName}（String，必填）；CONDITION 出边
 * {@code config.keyword}（String，可选，空/null 的边为默认边）。config 其余字段仍为
 * 不透明 Map 原样透传（Step7 禁令），本类只消费上述三个已定义键。
 * </p>
 * <p>
 * <b>明文 API Key 生命周期</b>（对齐 F01 惯例）：解密出的明文 Key 仅存在于局部变量，
 * 用于当次 {@code ChatModelFactory.build}，finally 中置 null，不进日志/异常/响应。
 * </p>
 * <p>
 * <b>死循环防护</b>：{@code maxSteps}（由调用方按 elements 节点数 × 2 计算）硬上限，
 * 超限抛 {@link GraphExecutionException}，不无限执行。
 * </p>
 *
 * @see GraphExecutionException
 */
public class AgentGraphInterpreter {

    // ==================== 节点类型常量（String 非 enum，D52 精神） ====================

    /** 开始节点 */
    public static final String NODE_TYPE_START = "START";

    /** 结束节点 */
    public static final String NODE_TYPE_END = "END";

    /** LLM 节点（config.agentModelConfigId 指定模型配置） */
    public static final String NODE_TYPE_LLM = "LLM";

    /** 工具节点（config.toolName 指定白名单工具） */
    public static final String NODE_TYPE_TOOL = "TOOL";

    /** 条件分支节点（纯路由点，按出边 config.keyword 子串匹配选路） */
    public static final String NODE_TYPE_CONDITION = "CONDITION";

    // ==================== 节点 config 键（本 Step 定义的执行契约） ====================

    /** LLM 节点 config 键：模型配置 id（Long） */
    public static final String CONFIG_KEY_AGENT_MODEL_CONFIG_ID = "agentModelConfigId";

    /** TOOL 节点 config 键：白名单工具名（String） */
    public static final String CONFIG_KEY_TOOL_NAME = "toolName";

    /** CONDITION 出边 config 键：关键词（String，空/null 的边为默认边） */
    public static final String CONFIG_KEY_KEYWORD = "keyword";

    // ==================== 依赖（纯构造注入，无 Spring 注解，可 mock 单测） ====================

    private final ChatModelFactory chatModelFactory;

    private final AgentToolCallbackFactory toolCallbackFactory;

    /** LLM 节点引用的模型配置（执行前校验已确认全部可解析到租户内配置，此处直接消费） */
    private final Map<Long, AgentModelConfig> modelConfigs;

    private final AesGcmCipher cipher;

    /** 工具白名单装载的租户过滤条件（null 时不显式过滤，由 MyBatis-Plus 租户拦截器隔离） */
    private final Long tenantId;

    /** 执行步数硬上限（防死循环兜底，调用方按 elements 节点数 × 2 计算） */
    private final int maxSteps;

    /**
     * @param chatModelFactory    动态模型客户端工厂（F01 既有，只读复用）
     * @param toolCallbackFactory 工具回调工厂（F01 既有，只读复用；null 时 TOOL 节点抛错）
     * @param modelConfigs        图内全部 LLM 节点引用的模型配置（id → 配置，执行前校验产物）
     * @param cipher              AES 解密器（解密 apiKeyCipher）
     * @param tenantId            当前租户（透传给工具白名单装载）
     * @param maxSteps            执行步数上限（防死循环）
     */
    public AgentGraphInterpreter(ChatModelFactory chatModelFactory,
                                 AgentToolCallbackFactory toolCallbackFactory,
                                 Map<Long, AgentModelConfig> modelConfigs,
                                 AesGcmCipher cipher,
                                 Long tenantId,
                                 int maxSteps) {
        this.chatModelFactory = chatModelFactory;
        this.toolCallbackFactory = toolCallbackFactory;
        this.modelConfigs = modelConfigs;
        this.cipher = cipher;
        this.tenantId = tenantId;
        this.maxSteps = maxSteps;
    }

    /**
     * 解释执行整图：START → 按 elements 顺序走节点/边 → END，返回最终累积文本。
     *
     * @param graph 已发布图（Step7 产物，config 不透明字段按本类契约消费）
     * @param input 请求入参文本（初始累积文本）
     * @return END 节点处的最终累积文本
     * @throws GraphExecutionException 条件分支无匹配且无默认边 / 步数超限 / 拓扑非法等运行时错误
     */
    public String run(ProcessGraph graph, String input) {
        List<GraphElement> elements = graph.getElements();
        GraphElement current = findStart(elements);
        String text = input;
        int steps = 0;
        while (!NODE_TYPE_END.equals(current.getType())) {
            if (++steps > maxSteps) {
                throw new GraphExecutionException("执行步数超限，图可能存在环路");
            }
            switch (current.getType()) {
                case NODE_TYPE_LLM -> text = callLlmNode(current, text);
                case NODE_TYPE_TOOL -> text = callToolNode(current, text);
                // START/CONDITION 为纯路由点，不动累积文本
                case NODE_TYPE_START, NODE_TYPE_CONDITION -> { }
                default -> throw new GraphExecutionException(
                        "不支持的节点类型: " + current.getType() + "（节点 " + current.getId() + "）");
            }
            current = findNode(nextNodeId(current, elements, text), elements);
        }
        return text;
    }

    // ==================== LLM 节点 ====================

    /**
     * LLM 节点执行：config.agentModelConfigId → 解密 Key → {@code ChatModelFactory.build}
     * → 以当前累积文本为 UserMessage 单跳调用（不带工具、不带历史）→ 输出覆盖累积文本。
     */
    private String callLlmNode(GraphElement node, String text) {
        Long modelConfigId = requireConfigId(node, CONFIG_KEY_AGENT_MODEL_CONFIG_ID);
        AgentModelConfig modelConfig = modelConfigs.get(modelConfigId);
        if (modelConfig == null) {
            // 执行前校验已拦截（PARAM_ERROR），此处为防御性兜底
            throw new GraphExecutionException("LLM 节点引用的模型配置不存在: " + modelConfigId);
        }
        String plainApiKey = null;
        try {
            if (modelConfig.getApiKeyCipher() != null && !modelConfig.getApiKeyCipher().isEmpty()) {
                plainApiKey = cipher.decrypt(modelConfig.getApiKeyCipher());
            }
            ChatModel chatModel = chatModelFactory.build(modelConfig, plainApiKey);
            ChatResponse response = chatModel.call(new Prompt(new UserMessage(text)));
            String output = response.getResult().getOutput().getText();
            if (output == null) {
                throw new GraphExecutionException("LLM 节点未返回文本: " + node.getId());
            }
            return output;
        } finally {
            plainApiKey = null;
        }
    }

    // ==================== TOOL 节点 ====================

    /**
     * TOOL 节点执行：config.toolName → 白名单装载结果中按名称精确匹配单个
     * {@link ToolCallback} → 以当前累积文本为入参直接调用 → 返回文本覆盖累积文本。
     * <p>
     * 与 F01 的区别：F01 把全部启用工具注入 LLM 由模型自行决定；本节点由图的拓扑
     * 决定调用哪个工具，只定位这一个回调并直接调用。每次执行即时装载（工厂非启动
     * 缓存语义：白名单配置变更即时生效）。
     * </p>
     */
    private String callToolNode(GraphElement node, String text) {
        String toolName = requireConfigString(node, CONFIG_KEY_TOOL_NAME);
        if (toolCallbackFactory == null) {
            throw new GraphExecutionException("工具工厂未装配，无法执行 TOOL 节点: " + toolName);
        }
        ToolCallback target = toolCallbackFactory.buildToolCallbacks(tenantId).stream()
                .filter(cb -> toolName.equals(cb.getToolDefinition().name()))
                .findFirst()
                .orElseThrow(() -> new GraphExecutionException(
                        "TOOL 节点引用的工具不存在或未启用: " + toolName));
        // 工具回调契约（与 F01 一致，工厂回执 §3 实测）：FunctionToolCallback 的 call()
        // 入参为 JSON 字符串字面量（LLM 按 {"type":"string"} schema 发送），白名单方法
        // 收到 JSON 编码字符串；返回值同样经 JSON 编码（实测 "echo:你好" → "\"echo:你好\""）。
        // 解释器把累积文本 JSON 编码后传入，并把返回的编码文本解码还原为纯文本后覆盖
        // 累积文本（图执行上下文是给下游节点/最终输出使用的用户可读文本，与 F01 中
        // LLM 直接消费编码文本的用途不同）。
        String result = target.call(JsonParser.toJson(text));
        String decoded = decodeIfJsonString(result);
        if (decoded == null) {
            throw new GraphExecutionException("TOOL 节点未返回文本: " + node.getId());
        }
        return decoded;
    }

    // ==================== 条件分支与选路 ====================

    /**
     * 确定下一节点 id：CONDITION 节点按 §2-C 关键词子串匹配（elements 原始顺序即优先级，
     * 不排序）；其余节点取唯一出边。
     *
     * @throws GraphExecutionException 条件无匹配且无默认边 / 默认边不唯一 / 出边数量非法
     */
    private String nextNodeId(GraphElement current, List<GraphElement> elements, String text) {
        List<GraphElement> edges = outgoingEdges(current, elements);
        if (NODE_TYPE_CONDITION.equals(current.getType())) {
            // 按 elements 出现顺序逐条匹配关键词，取第一个命中（不排序，原始顺序即优先级）
            for (GraphElement edge : edges) {
                String keyword = keywordOf(edge);
                if (keyword != null && text.contains(keyword)) {
                    return edge.getTarget();
                }
            }
            // 未命中 → 唯一无 keyword 边为默认边；默认边不存在 → 图设计缺陷，不静默吞掉
            List<GraphElement> defaultEdges = edges.stream()
                    .filter(e -> keywordOf(e) == null)
                    .toList();
            if (defaultEdges.size() == 1) {
                return defaultEdges.get(0).getTarget();
            }
            if (defaultEdges.isEmpty()) {
                throw new GraphExecutionException("条件分支无匹配且无默认边: " + current.getId());
            }
            throw new GraphExecutionException("条件分支默认边不唯一: " + current.getId());
        }
        // 非条件节点：必须且只能有一条出边（START/END/LLM/TOOL）
        if (edges.isEmpty()) {
            throw new GraphExecutionException("节点没有出边，无法继续执行: " + current.getId());
        }
        if (edges.size() > 1) {
            throw new GraphExecutionException("非条件节点的出边不唯一: " + current.getId());
        }
        return edges.get(0).getTarget();
    }

    /**
     * 读取边的条件关键词（执行契约键 {@code config.keyword}）：无 config / 键缺失 /
     * 值非 String / 空串均视为"无关键词"（该边为默认边候选）。
     */
    public static String keywordOf(GraphElement edge) {
        Map<String, Object> config = edge.getConfig();
        if (config == null) {
            return null;
        }
        Object keyword = config.get(CONFIG_KEY_KEYWORD);
        if (!(keyword instanceof String s) || s.isBlank()) {
            return null;
        }
        return s;
    }

    /** 当前节点的出边列表（kind=edge 且 source == 节点 id），按 elements 出现顺序 */
    private List<GraphElement> outgoingEdges(GraphElement node, List<GraphElement> elements) {
        List<GraphElement> edges = new ArrayList<>();
        for (GraphElement element : elements) {
            if ("edge".equals(element.getKind()) && node.getId().equals(element.getSource())) {
                edges.add(element);
            }
        }
        return edges;
    }

    // ==================== 内部辅助 ====================

    /** 定位唯一 START 节点（执行前校验已保证唯一，此处防御性兜底） */
    private GraphElement findStart(List<GraphElement> elements) {
        for (GraphElement element : elements) {
            if ("node".equals(element.getKind()) && NODE_TYPE_START.equals(element.getType())) {
                return element;
            }
        }
        throw new GraphExecutionException("图中不存在 START 节点");
    }

    /**
     * 工具返回解码：JSON 字符串字面量（如 {@code "hello"}）解码还原为纯文本；
     * 非 JSON 字符串（如外部工具返回的裸文本/JSON 对象）原样保留，不做转换。
     */
    private String decodeIfJsonString(String result) {
        if (result == null) {
            return null;
        }
        try {
            return JsonParser.fromJson(result, String.class);
        } catch (Exception e) {
            return result;
        }
    }

    /** 按 id 定位节点（防御：边引用了不存在的节点） */
    private GraphElement findNode(String id, List<GraphElement> elements) {
        for (GraphElement element : elements) {
            if ("node".equals(element.getKind()) && id.equals(element.getId())) {
                return element;
            }
        }
        throw new GraphExecutionException("边引用了不存在的节点: " + id);
    }

    /** 读取节点 config 中的 Long 型必填键（缺失/非数值 → 运行时错误，防御性兜底） */
    private Long requireConfigId(GraphElement node, String key) {
        Map<String, Object> config = node.getConfig();
        if (config == null || !(config.get(key) instanceof Number n)) {
            throw new GraphExecutionException("节点缺少 " + key + ": " + node.getId());
        }
        return n.longValue();
    }

    /** 读取节点 config 中的 String 型必填键（缺失/空白 → 运行时错误，防御性兜底） */
    private String requireConfigString(GraphElement node, String key) {
        Map<String, Object> config = node.getConfig();
        if (config == null || !(config.get(key) instanceof String s) || s.isBlank()) {
            throw new GraphExecutionException("节点缺少 " + key + ": " + node.getId());
        }
        return s;
    }

    /**
     * 图执行运行时错误（Step8 定义）：条件分支无匹配且无默认边 / 步数超限（疑似环路）/
     * 拓扑非法（出边数量、悬空引用、未知节点类型等）。由执行 Service 捕获并转
     * {@code success=false} + errorMessage（不上抛，与 F01 run() success=false 语义一致）。
     */
    public static class GraphExecutionException extends RuntimeException {

        public GraphExecutionException(String message) {
            super(message);
        }

        public GraphExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
