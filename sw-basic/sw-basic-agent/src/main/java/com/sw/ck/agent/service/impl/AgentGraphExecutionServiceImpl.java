package com.sw.ck.agent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw.ck.agent.dto.AgentGraphExecuteRespDTO;
import com.sw.ck.agent.dto.graph.GraphElement;
import com.sw.ck.agent.dto.graph.ProcessGraph;
import com.sw.ck.agent.entity.AgentGraphDef;
import com.sw.ck.agent.entity.AgentModelConfig;
import com.sw.ck.agent.entity.tool.AgentToolExternalConfig;
import com.sw.ck.agent.entity.tool.AgentToolInternalConfig;
import com.sw.ck.agent.mapper.AgentGraphDefMapper;
import com.sw.ck.agent.mapper.AgentModelConfigMapper;
import com.sw.ck.agent.mapper.tool.AgentToolExternalConfigMapper;
import com.sw.ck.agent.mapper.tool.AgentToolInternalConfigMapper;
import com.sw.ck.agent.orchestration.AgentGraphInterpreter;
import com.sw.ck.agent.orchestration.AgentToolCallbackFactory;
import com.sw.ck.agent.orchestration.ChatModelFactory;
import com.sw.ck.agent.service.AgentGraphExecutionService;
import com.sw.ck.common.crypto.AesGcmCipher;
import com.sw.ck.common.exception.BaseException;
import com.sw.ck.common.exception.CommonErrorCode;
import com.sw.ck.common.security.LoginContextProvider;
import com.sw.ck.common.service.BaseServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Agent 图执行 Service 实现（M07-F02 Step8 图解释执行引擎第一版）。
 * <p>
 * 流程（方案 §5）：加载图定义（requireEntity，NOT_FOUND 语义同 Step7）→ 校验
 * PUBLISHED → 反序列化 graph_json → 执行前校验（方案 §2-D 五项，任一失败即
 * PARAM_ERROR + 具体原因，不做部分执行）→ 调 {@link AgentGraphInterpreter} 解释执行。
 * </p>
 * <p>
 * <b>错误语义</b>：校验失败 → {@link BaseException}（全局惯例 HTTP 200 + body.code）；
 * 运行时错误（条件无匹配且无默认边 / 步数超限 / 模型或工具调用异常）→
 * {@code success=false} + errorMessage 返回（不上抛，与 F01 run() 语义一致）；
 * 不存在的 graphDefId / 跨租户 → NOT_FOUND。本 Step 不落库（不写会话/消息表，
 * 不新建执行日志表），返回值即结果。
 * </p>
 * <p>
 * <b>LLM 节点模型配置</b>：执行前校验一次性加载图内全部 LLM 节点引用的
 * {@link AgentModelConfig}（租户拦截器自动隔离）并传给解释器——单一查询、无
 * 校验与执行之间的 TOCTOU 窗口，解释器保持纯 Java 无 DB 访问。
 * </p>
 */
@Service
public class AgentGraphExecutionServiceImpl
        extends BaseServiceImpl<AgentGraphDefMapper, AgentGraphDef>
        implements AgentGraphExecutionService {

    /** 状态常量（varchar + String，不建 enum 类，D52 决策） */
    private static final String STATUS_PUBLISHED = "PUBLISHED";

    private final ObjectMapper objectMapper;
    private final AgentModelConfigMapper modelConfigMapper;
    private final AgentToolInternalConfigMapper internalToolMapper;
    private final AgentToolExternalConfigMapper externalToolMapper;
    private final ChatModelFactory chatModelFactory;
    private final AesGcmCipher cipher;
    private final LoginContextProvider loginContextProvider;

    /**
     * 工具回调工厂（可选注入，与 F01 同款模式）：{@code sw.agent.enabled} 未开启时
     * 为 null，TOOL 节点执行时由解释器抛运行时错误转 success=false。
     */
    @Autowired(required = false)
    private AgentToolCallbackFactory agentToolCallbackFactory;

    public AgentGraphExecutionServiceImpl(ObjectMapper objectMapper,
                                          AgentModelConfigMapper modelConfigMapper,
                                          AgentToolInternalConfigMapper internalToolMapper,
                                          AgentToolExternalConfigMapper externalToolMapper,
                                          ChatModelFactory chatModelFactory,
                                          AesGcmCipher cipher,
                                          LoginContextProvider loginContextProvider) {
        this.objectMapper = objectMapper;
        this.modelConfigMapper = modelConfigMapper;
        this.internalToolMapper = internalToolMapper;
        this.externalToolMapper = externalToolMapper;
        this.chatModelFactory = chatModelFactory;
        this.cipher = cipher;
        this.loginContextProvider = loginContextProvider;
    }

    @Override
    public AgentGraphExecuteRespDTO execute(Long graphDefId, String input) {
        // 参数校验（对齐 F01 run() 校验惯例：Service 层手动校验）
        if (input == null || input.isBlank()) {
            throw new BaseException(CommonErrorCode.PARAM_ERROR, "input 不能为空");
        }
        // NOT_FOUND（selectById 经租户拦截器自动过滤 tenant_id，同 Step7 requireEntity）
        AgentGraphDef entity = requireEntity(graphDefId);
        // 执行只认发布版本（草稿不可执行，对齐"发布版本是执行引用的稳定锚点"）
        if (!STATUS_PUBLISHED.equals(entity.getStatus())) {
            throw new BaseException(CommonErrorCode.PARAM_ERROR, "图未发布，无法执行");
        }
        ProcessGraph graph = parseGraph(entity.getGraphJson());
        if (graph == null || graph.getElements() == null || graph.getElements().isEmpty()) {
            throw new BaseException(CommonErrorCode.PARAM_ERROR, "图数据为空，无法执行");
        }
        // 执行前校验（§2-D）：任一失败即 PARAM_ERROR，不做部分执行；返回校验通过的
        // 模型配置映射（LLM 节点执行数据，解释器直接消费）
        Map<Long, AgentModelConfig> modelConfigs = validateForExecution(graph);

        long start = System.currentTimeMillis();
        AgentGraphExecuteRespDTO resp = new AgentGraphExecuteRespDTO();
        try {
            List<GraphElement> elements = graph.getElements();
            int nodeCount = (int) elements.stream()
                    .filter(e -> "node".equals(e.getKind()))
                    .count();
            // Step11 死循环防护预算（方案 §2.3）：maxSteps = 2 × 节点数 +
            // Σ(maxIterations of 所有 LOOP 节点) × 节点数。LOOP config 缺省 maxIterations
            // 用 DEFAULT_MAX_ITERATIONS 参与预算；无 LOOP 时退化为现状 2 × 节点数（回归
            // 安全）。给显式循环留足预算（近似最坏情况：每个循环跑满配置次数 × 全图节点
            // 数），避免"循环刚跑 1-2 次被误判死循环"；意外死循环 / JOIN 挂起死锁仍由
            // 全局兜底统一拦截（执行步数超限）。
            int loopBudget = 0;
            for (GraphElement element : elements) {
                if ("node".equals(element.getKind())
                        && AgentGraphInterpreter.NODE_TYPE_LOOP.equals(element.getType())) {
                    loopBudget += maxIterationsOf(element);
                }
            }
            int maxSteps = nodeCount * 2 + loopBudget * nodeCount;
            String output = new AgentGraphInterpreter(chatModelFactory, agentToolCallbackFactory,
                    modelConfigs, cipher, loginContextProvider.getTenantId(), maxSteps)
                    .run(graph, input);
            resp.setSuccess(true);
            resp.setOutput(output);
        } catch (Exception e) {
            // 运行时错误（GraphExecutionException / 模型或工具调用异常）：不上抛，
            // success=false + 异常摘要（与 F01 run() success=false 语义一致）
            resp.setSuccess(false);
            resp.setErrorMessage(summarizeError(e));
        }
        resp.setLatencyMs(System.currentTimeMillis() - start);
        return resp;
    }

    // ==================== 执行前校验（方案 §2-D） ====================

    /**
     * 执行前最小校验（非完整拓扑校验器，方案 §3 已裁定）：
     * ①PUBLISHED（调用方已校验）②唯一 START + 至少一个 END 可达 ③LLM 节点
     * agentModelConfigId 可解析到租户内 AgentModelConfig ④TOOL 节点 toolName 精确
     * 匹配 enabled=1 白名单 ⑤CONDITION 出边默认边唯一 ⑥FORK 出边数 ≥ 2 ⑦JOIN 入边数
     * ≥ 2 ⑧LOOP maxIterations（存在时）≥ 1。
     * <p>
     * 不做"循环体可达退出路径"静态分析（方案 §2.4 判断非必要：运行时已有 maxIterations
     * + 全局步数兜底双层防护，且循环体路径依赖动态变量匹配易误报）。既有 BFS visited
     * 去重天然容忍环。
     * </p>
     *
     * @return 图内全部 LLM 节点引用的模型配置（id → 配置）
     */
    private Map<Long, AgentModelConfig> validateForExecution(ProcessGraph graph) {
        List<GraphElement> elements = graph.getElements();
        List<GraphElement> nodes = elements.stream()
                .filter(e -> "node".equals(e.getKind()))
                .toList();

        // —— ② START 唯一 ——
        List<GraphElement> starts = nodes.stream()
                .filter(n -> AgentGraphInterpreter.NODE_TYPE_START.equals(n.getType()))
                .toList();
        if (starts.size() != 1) {
            throw new BaseException(CommonErrorCode.PARAM_ERROR,
                    "图中 START 节点必须唯一（当前 " + starts.size() + " 个）");
        }
        // —— ② 至少一个 END 可达（从唯一 START 沿边 BFS） ——
        if (!hasReachableEnd(starts.get(0), elements, nodes)) {
            throw new BaseException(CommonErrorCode.PARAM_ERROR, "图中不存在可达的 END 节点");
        }

        // —— ③④⑤ 按节点类型逐项校验 ——
        Map<Long, AgentModelConfig> modelConfigs = new HashMap<>();
        for (GraphElement node : nodes) {
            switch (node.getType()) {
                case AgentGraphInterpreter.NODE_TYPE_LLM -> {
                    Object idObj = configValue(node, AgentGraphInterpreter.CONFIG_KEY_AGENT_MODEL_CONFIG_ID);
                    if (!(idObj instanceof Number n)) {
                        throw new BaseException(CommonErrorCode.PARAM_ERROR,
                                "LLM 节点缺少模型配置引用: " + node.getId());
                    }
                    Long modelConfigId = n.longValue();
                    AgentModelConfig mc = modelConfigMapper.selectById(modelConfigId);
                    if (mc == null) {
                        throw new BaseException(CommonErrorCode.PARAM_ERROR,
                                "LLM 节点引用的模型配置不存在: " + modelConfigId);
                    }
                    modelConfigs.put(modelConfigId, mc);
                }
                case AgentGraphInterpreter.NODE_TYPE_TOOL -> {
                    Object nameObj = configValue(node, AgentGraphInterpreter.CONFIG_KEY_TOOL_NAME);
                    if (!(nameObj instanceof String toolName) || toolName.isBlank()) {
                        throw new BaseException(CommonErrorCode.PARAM_ERROR,
                                "TOOL 节点缺少工具名: " + node.getId());
                    }
                    if (!toolExists(toolName)) {
                        throw new BaseException(CommonErrorCode.PARAM_ERROR,
                                "工具节点引用的工具不存在或未启用: " + toolName);
                    }
                }
                case AgentGraphInterpreter.NODE_TYPE_CONDITION -> {
                    // ⑤ 默认边唯一（≥2 条无 keyword 边 → 图非法）；0 条默认边允许通过
                    // 校验，运行时未命中关键词时由解释器抛 GraphExecutionException
                    long defaultEdgeCount = outgoingEdges(node, elements).stream()
                            .filter(e -> AgentGraphInterpreter.keywordOf(e) == null)
                            .count();
                    if (defaultEdgeCount > 1) {
                        throw new BaseException(CommonErrorCode.PARAM_ERROR,
                                "条件分支默认边不唯一: " + node.getId());
                    }
                }
                case AgentGraphInterpreter.NODE_TYPE_FORK -> {
                    // ⑥ 扇出分支数 ≥ 2（每出边一个分支；少于 2 无并行语义）
                    if (outgoingEdges(node, elements).size() < 2) {
                        throw new BaseException(CommonErrorCode.PARAM_ERROR,
                                "FORK 节点扇出分支数必须 ≥ 2: " + node.getId());
                    }
                }
                case AgentGraphInterpreter.NODE_TYPE_JOIN -> {
                    // ⑦ 汇合入边数 ≥ 2（无汇合语义的单入边 JOIN 无意义）
                    if (incomingEdges(node, elements).size() < 2) {
                        throw new BaseException(CommonErrorCode.PARAM_ERROR,
                                "JOIN 节点汇合入边数必须 ≥ 2: " + node.getId());
                    }
                }
                case AgentGraphInterpreter.NODE_TYPE_LOOP -> {
                    // ⑧ maxIterations 存在且 <1 → 非法（缺失用默认 10，不报错）
                    Object maxIterations = configValue(node,
                            AgentGraphInterpreter.CONFIG_KEY_MAX_ITERATIONS);
                    if (maxIterations instanceof Number n && n.intValue() < 1) {
                        throw new BaseException(CommonErrorCode.PARAM_ERROR,
                                "LOOP 节点 maxIterations 必须 ≥ 1: " + node.getId());
                    }
                }
                default -> { /* START/END 及其他：无执行前校验 */ }
            }
        }
        return modelConfigs;
    }

    /** 节点 config 读取（config 为不透明 Map，本校验只按执行契约读已定义键，null 安全） */
    private Object configValue(GraphElement node, String key) {
        return node.getConfig() == null ? null : node.getConfig().get(key);
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

    /** 当前节点的入边列表（kind=edge 且 target == 节点 id），按 elements 出现顺序 */
    private List<GraphElement> incomingEdges(GraphElement node, List<GraphElement> elements) {
        List<GraphElement> edges = new ArrayList<>();
        for (GraphElement element : elements) {
            if ("edge".equals(element.getKind()) && node.getId().equals(element.getTarget())) {
                edges.add(element);
            }
        }
        return edges;
    }

    /**
     * LOOP 节点迭代上限（预算公式用）：config.maxIterations（Number → int；缺失/非数值 =
     * 默认 {@link AgentGraphInterpreter#DEFAULT_MAX_ITERATIONS}；<1 已被执行前校验拦截）。
     */
    private int maxIterationsOf(GraphElement node) {
        Object value = configValue(node, AgentGraphInterpreter.CONFIG_KEY_MAX_ITERATIONS);
        if (!(value instanceof Number n)) {
            return AgentGraphInterpreter.DEFAULT_MAX_ITERATIONS;
        }
        return n.intValue();
    }

    /** 从唯一 START 沿边 BFS，判断是否存在可达的 END 节点 */
    private boolean hasReachableEnd(GraphElement start, List<GraphElement> elements, List<GraphElement> nodes) {
        Map<String, String> typeById = new HashMap<>();
        for (GraphElement node : nodes) {
            typeById.put(node.getId(), node.getType());
        }
        Map<String, List<String>> adjacency = new HashMap<>();
        for (GraphElement edge : elements) {
            if ("edge".equals(edge.getKind()) && edge.getSource() != null && edge.getTarget() != null) {
                adjacency.computeIfAbsent(edge.getSource(), k -> new ArrayList<>()).add(edge.getTarget());
            }
        }
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(start.getId());
        while (!queue.isEmpty()) {
            String id = queue.poll();
            if (!visited.add(id)) {
                continue;
            }
            if (AgentGraphInterpreter.NODE_TYPE_END.equals(typeById.get(id))) {
                return true;
            }
            for (String next : adjacency.getOrDefault(id, List.of())) {
                if (!visited.contains(next)) {
                    queue.add(next);
                }
            }
        }
        return false;
    }

    /**
     * 工具白名单精确匹配：internal/external 两表任一存在 name 精确相等且 enabled=1 的记录。
     * enabled 用数字字面量 1（非 Boolean 参数）：H2/PG 下 SMALLINT 列比对惯例（Step4 现场实证）。
     */
    private boolean toolExists(String toolName) {
        Long internal = internalToolMapper.selectCount(
                Wrappers.<AgentToolInternalConfig>lambdaQuery()
                        .eq(AgentToolInternalConfig::getName, toolName)
                        .eq(AgentToolInternalConfig::getEnabled, 1));
        if (internal != null && internal > 0) {
            return true;
        }
        Long external = externalToolMapper.selectCount(
                Wrappers.<AgentToolExternalConfig>lambdaQuery()
                        .eq(AgentToolExternalConfig::getName, toolName)
                        .eq(AgentToolExternalConfig::getEnabled, 1));
        return external != null && external > 0;
    }

    // ==================== 内部辅助 ====================

    /** 按 id + 租户加载（selectById 经租户拦截器自动过滤），不存在抛 NOT_FOUND（同 Step7） */
    private AgentGraphDef requireEntity(Long id) {
        if (id == null) {
            throw new BaseException(CommonErrorCode.PARAM_ERROR, "id 不能为空");
        }
        AgentGraphDef entity = baseMapper.selectById(id);
        if (entity == null) {
            throw new BaseException(CommonErrorCode.NOT_FOUND);
        }
        return entity;
    }

    private ProcessGraph parseGraph(String graphJson) {
        if (graphJson == null || graphJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(graphJson, ProcessGraph.class);
        } catch (Exception e) {
            // 注：ServiceImpl 基类的 log 为 MyBatis Log 接口（org.apache.ibatis.logging.Log），
            // 不支持 {} 占位符，拼接消息（Step7 回执偏差 C 同款）
            log.warn("Failed to parse graph_json: " + e.getMessage());
            return null;
        }
    }

    /**
     * 异常摘要（对齐 F01 summarizeError）：沿 cause 链取最深层非空 message。
     * 只取 message 不含堆栈，杜绝明文 API Key 通过异常信息泄漏。
     */
    private String summarizeError(Throwable t) {
        Throwable cur = t;
        String best = null;
        while (cur != null) {
            if (cur.getMessage() != null && !cur.getMessage().isBlank()) {
                best = cur.getMessage();
            }
            cur = cur.getCause();
        }
        return best != null ? best : t.getClass().getSimpleName();
    }
}
