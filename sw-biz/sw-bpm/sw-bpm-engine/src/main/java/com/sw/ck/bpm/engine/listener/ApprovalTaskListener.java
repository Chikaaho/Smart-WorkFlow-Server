package com.sw.ck.bpm.engine.listener;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw.ck.bpm.api.exception.BpmErrorCode;
import com.sw.ck.bpm.api.spi.assignee.NodeApproverContext;
import com.sw.ck.bpm.api.spi.assignee.NodeApproverResolver;
import com.sw.ck.bpm.api.spi.assignee.NodeApproverType;
import com.sw.ck.common.exception.BaseException;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.task.service.delegate.DelegateTask;
import org.flowable.task.service.delegate.TaskListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 审批任务创建监听器（Flowable TaskListener）。
 * <p>
 * 在 UserTask 创建时（create 事件）被 Flowable 引擎回调，
 * 读取 BPMN 扩展属性中的 approver 配置，经 {@link NodeApproverResolver} 分发解析后设置 assignee。
 * </p>
 *
 * <h3>工作流程</h3>
 * <ol>
 *   <li>从 {@link DelegateTask#getTaskDefinitionKey()} 获取节点 ID（nodeKey）</li>
 *   <li>从 {@link DelegateTask#getProcessDefinitionId()} 加载 BpmnModel</li>
 *   <li>查找 UserTask 并读取 {@code approverConfig} 扩展元素（JSON）</li>
 *   <li>解析 approver 类型 + 值，按 type 从 {@code resolverMap} 分发</li>
 *   <li>取解析结果首个 userId 设为 assignee（v1 单人）</li>
 * </ol>
 *
 * <h3>错误码</h3>
 * <ul>
 *   <li>2200 — 解析结果为空（无人可分配）</li>
 *   <li>2201 — 审批人类型未实现（如 SCRIPT 桩）</li>
 *   <li>2202 — 审批人配置缺失（BPMN 扩展元素中无 approverConfig）</li>
 * </ul>
 */
@Component
public class ApprovalTaskListener implements TaskListener {

    private static final Logger log = LoggerFactory.getLogger(ApprovalTaskListener.class);

    private static final String APPROVER_CONFIG_ELEMENT = "approverConfig";
    private static final String FLOWABLE_NS = "http://flowable.org/bpmn";
    private static final String CONFIG_KEY_TYPE = "type";
    private static final String CONFIG_KEY_VALUE = "value";

    private final RepositoryService repositoryService;
    private final Map<String, NodeApproverResolver> resolverMap;
    private final ObjectMapper objectMapper;

    public ApprovalTaskListener(RepositoryService repositoryService,
                                @org.springframework.beans.factory.annotation.Qualifier("approverResolverMap")
                                Map<String, NodeApproverResolver> resolverMap,
                                ObjectMapper objectMapper) {
        this.repositoryService = repositoryService;
        this.resolverMap = resolverMap;
        this.objectMapper = objectMapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void notify(DelegateTask delegateTask) {
        String nodeKey = delegateTask.getTaskDefinitionKey();
        String processDefinitionId = delegateTask.getProcessDefinitionId();
        String processInstanceId = delegateTask.getProcessInstanceId();

        log.debug("TaskListener triggered: nodeKey={}, processDefinitionId={}, processInstanceId={}",
                nodeKey, processDefinitionId, processInstanceId);

        // 1. 加载 BpmnModel 并查找当前 UserTask
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
        if (bpmnModel == null) {
            log.error("BpmnModel not found for processDefinitionId={}", processDefinitionId);
            throw new BaseException(BpmErrorCode.APPROVER_CONFIG_MISSING.getCode(),
                    "无法加载流程定义模型: " + processDefinitionId);
        }

        UserTask userTask = (UserTask) bpmnModel.getFlowElement(nodeKey);
        if (userTask == null) {
            log.error("UserTask not found in BpmnModel: nodeKey={}", nodeKey);
            throw new BaseException(BpmErrorCode.APPROVER_CONFIG_MISSING.getCode(),
                    "BPMN 模型中未找到节点: " + nodeKey);
        }

        // 2. 读取 approverConfig（flowable 命名空间扩展属性）
        String approverJson = userTask.getAttributeValue(FLOWABLE_NS, APPROVER_CONFIG_ELEMENT);
        if (approverJson == null || approverJson.isBlank()) {
            log.error("Approver config missing in UserTask attributes: nodeKey={}", nodeKey);
            throw new BaseException(BpmErrorCode.APPROVER_CONFIG_MISSING);
        }

        // 3. 解析 approver 配置
        Map<String, Object> approverConfig;
        try {
            approverConfig = objectMapper.readValue(approverJson,
                    new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Failed to parse approver config JSON: {}", e.getMessage());
            throw new BaseException(BpmErrorCode.APPROVER_CONFIG_MISSING.getCode(),
                    "审批人配置 JSON 解析失败: " + e.getMessage());
        }

        String type = (String) approverConfig.get(CONFIG_KEY_TYPE);
        Object value = approverConfig.get(CONFIG_KEY_VALUE);

        if (type == null || type.isBlank()) {
            throw new BaseException(BpmErrorCode.APPROVER_CONFIG_MISSING.getCode(),
                    "审批人类型为空: nodeKey=" + nodeKey);
        }

        // 4. 按 type 分发解析
        NodeApproverResolver resolver = resolverMap.get(type);
        if (resolver == null) {
            log.error("Approver type '{}' not implemented (nodeKey={})", type, nodeKey);
            throw new BaseException(BpmErrorCode.APPROVER_TYPE_NOT_IMPLEMENTED.getCode(),
                    "未实现的审批人类型: " + type);
        }

        // 构建上下文
        Long tenantId = parseLong(delegateTask.getVariable("tenantId"));
        if (tenantId == null) {
            log.error("TenantId missing in process variables: processInstanceId={}, nodeKey={}",
                    processInstanceId, nodeKey);
            throw new BaseException(BpmErrorCode.APPROVER_TENANT_ID_MISSING);
        }
        NodeApproverContext ctx = NodeApproverContext.builder()
                .tenantId(tenantId)
                .processInstanceId(processInstanceId)
                .nodeKey(nodeKey)
                .businessKey((String) delegateTask.getVariable("recordId"))
                .formKey((String) delegateTask.getVariable("formKey"))
                .approverValue(value)
                .initiatorUserId(parseLong(delegateTask.getVariable("submitter")))
                .build();

        // 5. 解析审批人
        List<String> userIds = resolver.resolve(ctx);
        if (userIds == null || userIds.isEmpty()) {
            log.error("Approver resolution returned empty: nodeKey={}, type={}", nodeKey, type);
            throw new BaseException(BpmErrorCode.APPROVER_RESOLVE_EMPTY);
        }

        // v1 取首个作为 assignee
        String assignee = userIds.get(0);
        delegateTask.setAssignee(assignee);

        log.info("Task assignee set: taskId={}, nodeKey={}, assignee={}",
                delegateTask.getId(), nodeKey, assignee);
    }

    private Long parseLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
