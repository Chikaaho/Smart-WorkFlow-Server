package com.sw.ck.bpm.engine.translator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw.ck.bpm.api.dto.GraphElement;
import com.sw.ck.bpm.api.dto.ProcessGraph;
import com.sw.ck.bpm.api.exception.BpmErrorCode;
import com.sw.ck.common.exception.BaseException;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowableListener;
import org.flowable.bpmn.model.ImplementationType;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.UserTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 流程图画布模型 → BPMN 2.0 模型翻译器。
 * <p>
 * 纯函数：同一 {@link ProcessGraph} 输入产出确定 {@link BpmnModel} 输出，
 * 可对 BpmnModel 结构断言，不需启动 Flowable 引擎。
 * </p>
 *
 * <h3>映射规则</h3>
 * <ul>
 *   <li>START → {@link StartEvent}</li>
 *   <li>END → {@link EndEvent}</li>
 *   <li>APPROVAL → {@link UserTask}（assignee 不写死，携 nodeKey + approver 扩展属性）</li>
 *   <li>顺序边 → {@link SequenceFlow}</li>
 * </ul>
 *
 * <h3>扩展属性</h3>
 * APPROVAL 节点翻译为 UserTask 时：
 * <ul>
 *   <li>通过 {@link ExtensionAttribute}（{@code flowable:approverConfig} 命名空间属性）
 *       写入 approver 配置（type + value JSON）</li>
 *   <li>通过 {@link FlowableListener} 挂载 {@code ApprovalTaskListener}（create 事件）</li>
 *   <li>assignee 不设值（不写死，不设 ${approver}）</li>
 * </ul>
 */
public class GraphToBpmnTranslator {

    private static final Logger log = LoggerFactory.getLogger(GraphToBpmnTranslator.class);

    private static final String KIND_NODE = "node";
    private static final String KIND_EDGE = "edge";
    private static final String TYPE_START = "START";
    private static final String TYPE_END = "END";
    private static final String TYPE_APPROVAL = "APPROVAL";

    private static final String APPROVER_CONFIG_ELEMENT = "approverConfig";

    /** Flowable 扩展命名空间（BPMN 2.0 XSD 允许 {@code flowable:*} 属性） */
    private static final String FLOWABLE_NS = "http://flowable.org/bpmn";
    private static final String FLOWABLE_PREFIX = "flowable";

    /** Spring bean name of ApprovalTaskListener（用于 delegation expression） */
    private static final String TASK_LISTENER_BEAN = "approvalTaskListener";

    private final ObjectMapper objectMapper;

    public GraphToBpmnTranslator() {
        this.objectMapper = new ObjectMapper();
    }

    public GraphToBpmnTranslator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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

        return bpmnModel;
    }

    /**
     * 翻译单个节点。
     */
    private FlowElement translateNode(GraphElement node) {
        String type = node.getType();
        if (type == null) {
            log.warn("Skipping node with null type: id={}", node.getId());
            return null;
        }

        return switch (type) {
            case TYPE_START -> createStartEvent(node);
            case TYPE_END -> createEndEvent(node);
            case TYPE_APPROVAL -> createUserTask(node);
            default -> {
                log.warn("Unknown node type '{}', skipping: id={}", type, node.getId());
                yield null;
            }
        };
    }

    private StartEvent createStartEvent(GraphElement node) {
        StartEvent startEvent = new StartEvent();
        startEvent.setId(node.getId());
        startEvent.setName("Start");
        return startEvent;
    }

    private EndEvent createEndEvent(GraphElement node) {
        EndEvent endEvent = new EndEvent();
        endEvent.setId(node.getId());
        endEvent.setName("End");
        return endEvent;
    }

    /**
     * 创建审批节点 UserTask。
     * <p>
     * assignee 不写死：不设值、不设 ${approver} 表达式。
     * 挂载 create 事件 TaskListener（{@link #TASK_LISTENER_CLASS}）。
     * 通过 {@link ExtensionElement} 写入 approverConfig（type + value JSON）。
     * 通过 {@link FlowableListener} 的 FieldExtension 写入 nodeKey。
     * </p>
     */
    @SuppressWarnings("unchecked")
    private UserTask createUserTask(GraphElement node) {
        UserTask userTask = new UserTask();
        userTask.setId(node.getId());
        // 从 config 中读取 name，若无则用默认
        String nodeName = extractNodeName(node);
        userTask.setName(nodeName);

        // assignee 不写死（不设值，不走 ${approver} 表达式）
        // assignee 留空，由 TaskListener 在 create 事件中设置

        // 1. 挂载 create 事件 TaskListener（delegation expression → Spring bean）
        //    使用 delegation expression 而非 class 类型，确保 Spring Boot 下
        //    Flowable 通过 Spring 表达式管理器将 ${approvalTaskListener} 解析为
        //    ApprovalTaskListener Spring bean（带完整依赖注入）。
        FlowableListener listener = new FlowableListener();
        listener.setEvent("create");
        listener.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION);
        listener.setImplementation("${" + TASK_LISTENER_BEAN + "}");
        List<FlowableListener> taskListeners = userTask.getTaskListeners();
        if (taskListeners == null) {
            taskListeners = new ArrayList<>();
            userTask.setTaskListeners(taskListeners);
        }
        taskListeners.add(listener);

        // 2. 写入 approverConfig 扩展元素
        // 从 node.config.approver 读取 {type, value}
        // 通过 UserTask.setAttributeValue() 存储，TaskListener 从 BpmnModel 读取
        Map<String, Object> config = node.getConfig();
        if (config != null && config.containsKey("approver")) {
            Object approverObj = config.get("approver");
            try {
                String approverJson = objectMapper.writeValueAsString(approverObj);
                ExtensionAttribute attr = new ExtensionAttribute(
                        APPROVER_CONFIG_ELEMENT, approverJson);
                attr.setNamespace(FLOWABLE_NS);
                attr.setNamespacePrefix(FLOWABLE_PREFIX);
                userTask.addAttribute(attr);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize approver config for node {}: {}",
                        node.getId(), e.getMessage());
            }
        }

        return userTask;
    }

    /**
     * 从节点配置中提取名称。
     */
    private String extractNodeName(GraphElement node) {
        if (node.getConfig() != null && node.getConfig().containsKey("name")) {
            Object nameObj = node.getConfig().get("name");
            if (nameObj instanceof String name && !name.isBlank()) {
                return name;
            }
        }
        return "审批";
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
