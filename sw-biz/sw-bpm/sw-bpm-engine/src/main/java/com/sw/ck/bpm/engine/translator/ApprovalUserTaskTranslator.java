package com.sw.ck.bpm.engine.translator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw.ck.bpm.api.dto.GraphElement;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowableListener;
import org.flowable.bpmn.model.ImplementationType;
import org.flowable.bpmn.model.UserTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * APPROVAL 节点翻译器 —— 画布 APPROVAL 节点 → BPMN {@link UserTask}。
 * <p>
 * 自 B2（M04-F08-01）起从 {@link GraphToBpmnTranslator} 的 switch 中拆出，
 * 经 {@link NodeTypeTranslator} 注册表分发，翻译行为与拆分前逐字节一致。
 * </p>
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
public class ApprovalUserTaskTranslator implements NodeTypeTranslator {

    private static final Logger log = LoggerFactory.getLogger(ApprovalUserTaskTranslator.class);

    private static final String APPROVER_CONFIG_ELEMENT = "approverConfig";

    /** Flowable 扩展命名空间（BPMN 2.0 XSD 允许 {@code flowable:*} 属性） */
    private static final String FLOWABLE_NS = "http://flowable.org/bpmn";
    private static final String FLOWABLE_PREFIX = "flowable";

    /** Spring bean name of ApprovalTaskListener（用于 delegation expression） */
    private static final String TASK_LISTENER_BEAN = "approvalTaskListener";

    private final ObjectMapper objectMapper;

    public ApprovalUserTaskTranslator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return "APPROVAL";
    }

    /**
     * 创建审批节点 UserTask。
     * <p>
     * assignee 不写死：不设值、不设 ${approver} 表达式。
     * 挂载 create 事件 TaskListener（delegation expression → Spring bean）。
     * 通过 {@link ExtensionAttribute} 写入 approverConfig（type + value JSON）。
     * </p>
     */
    @SuppressWarnings("unchecked")
    @Override
    public FlowElement translate(GraphElement node) {
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
}
