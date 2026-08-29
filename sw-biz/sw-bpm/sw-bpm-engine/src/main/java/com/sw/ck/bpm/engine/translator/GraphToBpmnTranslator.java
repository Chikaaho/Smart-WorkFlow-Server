package com.sw.ck.bpm.engine.translator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw.ck.bpm.api.dto.GraphElement;
import com.sw.ck.bpm.api.dto.ProcessGraph;
import com.sw.ck.bpm.api.exception.BpmErrorCode;
import com.sw.ck.common.exception.BaseException;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.SequenceFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 流程图画布模型 → BPMN 2.0 模型翻译器。
 * <p>
 * 纯函数：同一 {@link ProcessGraph} 输入产出确定 {@link BpmnModel} 输出，
 * 可对 BpmnModel 结构断言，不需启动 Flowable 引擎。
 * </p>
 *
 * <h3>节点翻译注册化（M04-F08-01 B2）</h3>
 * 节点类型 → Flowable 元素的翻译从 switch 硬编码改为按 {@link NodeTypeTranslator}
 * 注册表 Map 分发（仿 {@code approverResolverMap} 的 {@code Map<String, ...>} 先例）：
 * <ul>
 *   <li>默认注册 START / END / APPROVAL 三个内置翻译器（行为与拆分前逐字节一致）</li>
 *   <li>新增节点类型 = 实现 {@link NodeTypeTranslator} 并经构造器注册，
 *       本翻译器零改动（可插拔性证明见测试）</li>
 *   <li>未注册翻译器的类型按既有语义 warn + skip（返回 null，不产出元素）</li>
 * </ul>
 *
 * <h3>映射规则</h3>
 * <ul>
 *   <li>START → {@link org.flowable.bpmn.model.StartEvent}（见 {@link StartEventTranslator}）</li>
 *   <li>END → {@link org.flowable.bpmn.model.EndEvent}（见 {@link EndEventTranslator}）</li>
 *   <li>APPROVAL → {@link org.flowable.bpmn.model.UserTask}（assignee 不写死，
 *       携 nodeKey + approver 扩展属性，见 {@link ApprovalUserTaskTranslator}）</li>
 *   <li>顺序边 → {@link SequenceFlow}</li>
 * </ul>
 */
public class GraphToBpmnTranslator {

    private static final Logger log = LoggerFactory.getLogger(GraphToBpmnTranslator.class);

    private static final String KIND_NODE = "node";
    private static final String KIND_EDGE = "edge";

    private final ObjectMapper objectMapper;

    /** 节点类型 → 翻译器注册表（按 type 字符串 key 分发，替代 switch） */
    private final Map<String, NodeTypeTranslator> translatorMap;

    public GraphToBpmnTranslator() {
        this(new ObjectMapper());
    }

    public GraphToBpmnTranslator(ObjectMapper objectMapper) {
        this(objectMapper, List.of());
    }

    /**
     * 可插拔构造器：默认内置翻译器 + 插件翻译器合并注册。
     * <p>
     * 新增节点类型仅需在此注册一个 {@link NodeTypeTranslator} 实现，
     * 翻译与分发逻辑（{@link #translate(ProcessGraph)}）零改动。
     * 插件与内置类型冲突时插件覆盖内置（警告日志）。
     * </p>
     *
     * @param pluginTranslators 插件翻译器列表（可为空）
     */
    public GraphToBpmnTranslator(ObjectMapper objectMapper,
                                 List<NodeTypeTranslator> pluginTranslators) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper must not be null");
        this.translatorMap = new HashMap<>();
        register(new StartEventTranslator());
        register(new EndEventTranslator());
        register(new ApprovalUserTaskTranslator(objectMapper));
        if (pluginTranslators != null) {
            for (NodeTypeTranslator plugin : pluginTranslators) {
                register(plugin);
            }
        }
        log.debug("GraphToBpmnTranslator initialized with {} node type translators",
                translatorMap.size());
    }

    private void register(NodeTypeTranslator translator) {
        NodeTypeTranslator previous = translatorMap.put(translator.type(), translator);
        if (previous != null) {
            log.warn("Node type '{}' translator overridden: {} -> {}",
                    translator.type(), previous.getClass().getSimpleName(),
                    translator.getClass().getSimpleName());
        }
    }

    /**
     * 将 {@link ProcessGraph} 翻译为 {@link BpmnModel}。
     *
     * @param graph 流程设计器图模型（非空，已通过拓扑校验）
     * @return 标准 BPMN 2.0 模型
     * @throws BaseException 翻译失败时抛出 TRANSLATION_FAILED（2102）
     */
    public BpmnModel translate(ProcessGraph graph) {
        Objects.requireNonNull(graph, "ProcessGraph must not be null");
        try {
            return doTranslate(graph);
        } catch (Exception e) {
            log.error("BPMN translation failed: {}", e.getMessage(), e);
            throw new BaseException(BpmErrorCode.TRANSLATION_FAILED.getCode(),
                    "图翻译为 BPMN 失败: " + e.getMessage());
        }
    }

    private BpmnModel doTranslate(ProcessGraph graph) {
        List<GraphElement> elements = graph.getElements();
        if (elements == null || elements.isEmpty()) {
            throw new BaseException(BpmErrorCode.TRANSLATION_FAILED);
        }

        // 分离节点与边
        List<GraphElement> nodes = elements.stream()
                .filter(e -> KIND_NODE.equals(e.getKind()))
                .toList();
        List<GraphElement> edges = elements.stream()
                .filter(e -> KIND_EDGE.equals(e.getKind()))
                .toList();

        // 构建 BPMN Process（用全限定名解决 java.lang.Process 冲突）
        org.flowable.bpmn.model.Process process = new org.flowable.bpmn.model.Process();
        process.setId(graph.getProcessKey());
        process.setName(graph.getName() != null ? graph.getName() : graph.getProcessKey());
        process.setExecutable(true);

        // 缓存节点 ID → BPMN FlowElement
        Map<String, FlowElement> flowElementMap = new LinkedHashMap<>();

        // 1. 翻译所有节点
        for (GraphElement node : nodes) {
            FlowElement flowElement = translateNode(node);
            if (flowElement != null) {
                flowElementMap.put(node.getId(), flowElement);
                process.addFlowElement(flowElement);
            }
        }

        // 2. 翻译所有边
        for (GraphElement edge : edges) {
            SequenceFlow sequenceFlow = translateEdge(edge, flowElementMap);
            if (sequenceFlow != null) {
                process.addFlowElement(sequenceFlow);
            }
        }

        // 3. 构建 BpmnModel
        BpmnModel bpmnModel = new BpmnModel();
        bpmnModel.addProcess(process);

        // 4. 生成 DI（图形交换信息）：图模型不携带坐标，按节点出现顺序做水平链式自动布局。
        //    缺失 DI 时 bpmn-js 会渲染空白画布（流程图不可见）。
        applyDiagramInterchange(bpmnModel, process, nodes, flowElementMap);

        return bpmnModel;
    }

    /** 布局常量：水平间距 / 中心线纵坐标 / 各类型节点尺寸 */
    private static final int DI_NODE_GAP = 170;
    private static final int DI_CENTER_Y = 220;
    private static final int DI_EVENT_SIZE = 30;
    private static final int DI_TASK_WIDTH = 100;
    private static final int DI_TASK_HEIGHT = 80;
    private static final int DI_START_X = 150;

    /**
     * 为节点与顺序边写入 DI 坐标。
     * <p>
     * 节点按出现顺序水平排布（事件 30×30 圆，任务 100×80 矩形，同一中心线）；
     * 边取源节点右中点到目标节点左中点的直线航点。
     * </p>
     */
    private void applyDiagramInterchange(BpmnModel bpmnModel,
                                         org.flowable.bpmn.model.Process process,
                                         List<GraphElement> nodes,
                                         Map<String, FlowElement> flowElementMap) {
        // 节点槽位（x, y, width, height）：按出现顺序水平排布，同一中心线
        Map<String, int[]> boxByNodeId = new LinkedHashMap<>();
        int slot = 0;
        for (GraphElement node : nodes) {
            FlowElement element = flowElementMap.get(node.getId());
            if (element == null) {
                continue;
            }
            boolean isEvent = !(element instanceof org.flowable.bpmn.model.UserTask);
            int width = isEvent ? DI_EVENT_SIZE : DI_TASK_WIDTH;
            int height = isEvent ? DI_EVENT_SIZE : DI_TASK_HEIGHT;
            int x = DI_START_X + slot * DI_NODE_GAP + (DI_TASK_WIDTH - width) / 2;
            int y = DI_CENTER_Y - height / 2;
            boxByNodeId.put(node.getId(), new int[] {x, y, width, height});

            org.flowable.bpmn.model.GraphicInfo gi = new org.flowable.bpmn.model.GraphicInfo();
            gi.setX(x);
            gi.setY(y);
            gi.setWidth(width);
            gi.setHeight(height);
            bpmnModel.addGraphicInfo(element.getId(), gi);
            slot++;
        }

        // 边航点：源右中 → 目标左中。顺序边不进 flowElementMap，须从 Process 取。
        for (FlowElement element : process.findFlowElementsOfType(SequenceFlow.class)) {
            SequenceFlow flow = (SequenceFlow) element;
            int[] src = boxByNodeId.get(flow.getSourceRef());
            int[] tgt = boxByNodeId.get(flow.getTargetRef());
            if (src == null || tgt == null) {
                continue;
            }
            org.flowable.bpmn.model.GraphicInfo from = new org.flowable.bpmn.model.GraphicInfo();
            from.setX(src[0] + src[2]);
            from.setY(src[1] + src[3] / 2.0);
            org.flowable.bpmn.model.GraphicInfo to = new org.flowable.bpmn.model.GraphicInfo();
            to.setX(tgt[0]);
            to.setY(tgt[1] + tgt[3] / 2.0);
            bpmnModel.addFlowGraphicInfoList(flow.getId(), List.of(from, to));
        }
    }

    /**
     * 翻译单个节点 —— 按 type 从注册表分发，无注册翻译器时 warn + skip。
     */
    private FlowElement translateNode(GraphElement node) {
        String type = node.getType();
        if (type == null) {
            log.warn("Skipping node with null type: id={}", node.getId());
            return null;
        }

        NodeTypeTranslator translator = translatorMap.get(type);
        if (translator == null) {
            log.warn("Unknown node type '{}', skipping: id={}", type, node.getId());
            return null;
        }
        return translator.translate(node);
    }

    /**
     * 翻译顺序边。
     */
    private SequenceFlow translateEdge(GraphElement edge,
                                       Map<String, FlowElement> flowElementMap) {
        String source = edge.getSource();
        String target = edge.getTarget();

        if (source == null || target == null) {
            log.warn("Skipping edge with null source/target: id={}", edge.getId());
            return null;
        }

        SequenceFlow sequenceFlow = new SequenceFlow(source, target);
        sequenceFlow.setId(edge.getId());
        return sequenceFlow;
    }
}
