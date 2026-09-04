package com.sw.ck.bpm.process.controller;

import com.sw.ck.bpm.api.dto.BpmTaskDTO;
import com.sw.ck.bpm.api.event.BpmDeviceCommandEvent;
import com.sw.ck.bpm.api.event.BpmNotifyEvent;
import com.sw.ck.bpm.api.event.BpmNotifyTrigger;
import com.sw.ck.bpm.api.facade.BpmTaskFacade;
import com.sw.ck.bpm.process.dto.ApprovalHistoryItemDTO;
import com.sw.ck.bpm.process.dto.ApprovalAction;
import com.sw.ck.bpm.process.dto.ApprovalActionRequest;
import com.sw.ck.bpm.process.entity.ApprovalActionRecord;
import com.sw.ck.bpm.process.dto.ProcessedTaskRespDTO;
import com.sw.ck.bpm.process.dto.TaskDetailRespDTO;
import com.sw.ck.bpm.process.dto.TodoTaskRespDTO;
import com.sw.ck.bpm.process.entity.BpmInstance;
import com.sw.ck.bpm.process.entity.BpmProcessDef;
import com.sw.ck.bpm.process.entity.InstanceStatusEnum;
import com.sw.ck.bpm.process.service.BpmInstanceService;
import com.sw.ck.bpm.process.service.BpmProcessDefService;
import com.sw.ck.bpm.process.service.ApprovalActionService;
import com.sw.ck.bpm.process.validator.ApprovalOpinionValidator;
import com.sw.ck.bpm.api.dto.GraphElement;
import com.sw.ck.bpm.api.dto.ProcessGraph;
import com.sw.ck.bpm.api.participant.ParticipantSnapshotRecorder;
import com.sw.ck.common.event.DomainEventPublisher;
import com.sw.ck.common.exception.BaseException;
import com.sw.ck.common.exception.CommonErrorCode;
import com.sw.ck.common.page.PageParam;
import com.sw.ck.common.page.PageResult;
import com.sw.ck.common.response.R;
import com.sw.ck.system.api.user.UserQueryFacade;
import com.sw.ck.security.holder.LoginUser;
import com.sw.ck.security.holder.LoginUserHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 待办中心控制器。
 * <p>
 * 提供两个最小接口：
 * <ul>
 *   <li>{@code GET /workflow/tasks/todo} — 当前租户 + 当前用户的待办列表</li>
 *   <li>{@code POST /workflow/tasks/{taskId}/complete} — 同意（带越权校验）</li>
 * </ul>
 * </p>
 *
 * <h3>安全</h3>
 * <ul>
 *   <li>两接口均需登录（默认走鉴权，无需加 permit 白名单）</li>
 *   <li>{@code complete} 前置越权校验：{@code taskTenantId == 当前租户} 且
 *       {@code assignee == 当前用户}，任一不符抛 {@link BaseException} 拒绝</li>
 *   <li>待办查询按 {@code taskTenantId + taskAssignee} 双条件过滤，不依赖 ORM 拦截器</li>
 * </ul>
 *
 * <h3>流程结束判定</h3>
 * {@code complete} 成功后查 Facade 该 processInstanceId 是否无活动实例
 *（单节点 complete 后流程即结束），若是则更新
 * {@code sw_bpm_instance.status = APPROVED}。
 *
 * <h3>防腐</h3>
 * 本 Controller 不 import 任何 Flowable 类型；所有引擎操作经
 * {@link BpmTaskFacade} 完成。BpmTaskDTO(Date) → TodoTaskRespDTO(LocalDateTime)
 * 富化转换在 process 侧显式进行。
 */
@RestController
@RequestMapping("/workflow/tasks")
public class BpmTodoController {

    private static final Logger log = LoggerFactory.getLogger(BpmTodoController.class);

    private final BpmTaskFacade bpmTaskFacade;
    private final BpmInstanceService bpmInstanceService;
    private final BpmProcessDefService bpmProcessDefService;
    private final DomainEventPublisher domainEventPublisher;
    private final UserQueryFacade userQueryFacade;
    private final ApprovalActionService approvalActionService;
    private final ParticipantSnapshotRecorder participantSnapshotRecorder;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public BpmTodoController(BpmTaskFacade bpmTaskFacade,
                             BpmInstanceService bpmInstanceService,
                             BpmProcessDefService bpmProcessDefService,
                             DomainEventPublisher domainEventPublisher,
                             UserQueryFacade userQueryFacade) {
        this(bpmTaskFacade, bpmInstanceService, bpmProcessDefService, domainEventPublisher,
                userQueryFacade, null, null);
    }

    public BpmTodoController(BpmTaskFacade bpmTaskFacade,
                             BpmInstanceService bpmInstanceService,
                             BpmProcessDefService bpmProcessDefService,
                             DomainEventPublisher domainEventPublisher,
                             UserQueryFacade userQueryFacade,
                             ApprovalActionService approvalActionService,
                             com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this(bpmTaskFacade, bpmInstanceService, bpmProcessDefService, domainEventPublisher,
                userQueryFacade, approvalActionService, objectMapper, null);
    }

    @Autowired
    public BpmTodoController(BpmTaskFacade bpmTaskFacade,
                             BpmInstanceService bpmInstanceService,
                             BpmProcessDefService bpmProcessDefService,
                             DomainEventPublisher domainEventPublisher,
                             UserQueryFacade userQueryFacade,
                             ApprovalActionService approvalActionService,
                             com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                             ParticipantSnapshotRecorder participantSnapshotRecorder) {
        this.bpmTaskFacade = bpmTaskFacade;
        this.bpmInstanceService = bpmInstanceService;
        this.bpmProcessDefService = bpmProcessDefService;
        this.domainEventPublisher = domainEventPublisher;
        this.userQueryFacade = userQueryFacade;
        this.approvalActionService = approvalActionService;
        this.objectMapper = objectMapper;
        this.participantSnapshotRecorder = participantSnapshotRecorder;
    }

    /**
     * 当前用户待办列表（分页）。
     * <p>
     * 按 {@code taskTenantId + taskAssignee} 双条件查询，
     * 将 BpmTaskDTO 富化为 TodoTaskRespDTO（Date→LocalDateTime 转换 + formKey + processName 富化）。
     * </p>
     *
     * @param pageParam 分页参数（pageNum 从 1 开始）
     * @return 分页待办任务列表
     */
    @GetMapping("/todo")
    public R<PageResult<TodoTaskRespDTO>> todo(PageParam pageParam) {
        LoginUser loginUser = LoginUserHolder.get();
        String tenantId = String.valueOf(loginUser.getTenantId());
        String assignee = String.valueOf(loginUser.getUserId());

        long offset = (pageParam.getPageNum() - 1) * pageParam.getPageSize();
        int limit = (int) pageParam.getPageSize();

        List<BpmTaskDTO> tasks = bpmTaskFacade.queryTodoPage(tenantId, assignee, (int) offset, limit);
        long total = bpmTaskFacade.countTodo(tenantId, assignee);

        List<TodoTaskRespDTO> dtos = tasks.stream()
                .map(this::toTodoTaskDTO)
                .collect(Collectors.toList());

        log.debug("待办查询: tenantId={}, assignee={}, total={}, pageNum={}, pageSize={}",
                tenantId, assignee, total, pageParam.getPageNum(), pageParam.getPageSize());

        PageResult<TodoTaskRespDTO> pageResult = new PageResult<>();
        pageResult.setRecords(dtos);
        pageResult.setTotal(total);
        pageResult.setPageNum(pageParam.getPageNum());
        pageResult.setPageSize(pageParam.getPageSize());

        return R.ok(pageResult);
    }

    /**
     * 完成审批（同意）。
     * <p>
     * 前置越权校验（租户 + 审批人匹配），完成后若流程结束则更新实例状态为 APPROVED。
     * </p>
     *
     * @param taskId Flowable task ID
     * @return 操作成功
     * @throws BaseException 任务不存在 / 越权时抛出
     */
    /** 兼容既有 Java 调用方；HTTP 映射使用带 body 的重载。 */
    public R<Void> complete(String taskId) {
        return handleAction(taskId, null);
    }

    @Transactional
    @PostMapping("/{taskId}/complete")
    public R<Void> complete(@PathVariable String taskId,
                            @RequestBody(required = false) ApprovalActionRequest request) {
        if (request == null) {
            return handleAction(taskId, null);
        }
        request.setAction(ApprovalAction.APPROVE);
        return handleAction(taskId, request);
    }

    private R<Void> handleAction(String taskId, ApprovalActionRequest request) {
        LoginUser loginUser = LoginUserHolder.get();

        // 1. 查询 task（经 Facade 包装，无 Flowable 泄漏）
        BpmTaskDTO task = bpmTaskFacade.getTask(taskId);
        if (task == null) {
            if (approvalActionService != null && approvalActionService.findByTaskId(taskId) != null) {
                throw new BaseException(com.sw.ck.bpm.api.exception.BpmErrorCode.APPROVAL_ALREADY_HANDLED);
            }
            throw new BaseException(CommonErrorCode.NOT_FOUND.getCode(), "任务不存在");
        }

        // 2. 越权校验：审批人
        boolean assigned = String.valueOf(loginUser.getUserId()).equals(task.getAssignee());
        if (!assigned && !bpmTaskFacade.canHandle(taskId, String.valueOf(loginUser.getUserId()))) {
            log.warn("越权拒绝（审批人不匹配）: taskId={}, taskAssignee={}, currentUserId={}",
                    taskId, task.getAssignee(), loginUser.getUserId());
            throw new BaseException(CommonErrorCode.FORBIDDEN.getCode(), "无权处理该任务");
        }

        String processInstanceId = task.getProcessInstanceId();

        BpmInstance instance = bpmInstanceService.findByProcessInstanceId(processInstanceId).orElse(null);
        if (instance != null && InstanceStatusEnum.FAILED.getCode().equals(instance.getStatus())) {
            log.warn("失败实例拒绝继续审批: processInstanceId={}, taskId={}, userId={}",
                    processInstanceId, taskId, loginUser.getUserId());
            throw new BaseException(com.sw.ck.bpm.api.exception.BpmErrorCode.INSTANCE_FAILED);
        }

        // 2.5 流程结束前读取设备透传变量（实例结束后 Runtime 变量不可查）
        String productId = asString(bpmTaskFacade.getVariable(processInstanceId, "productId"));
        String deviceName = asString(bpmTaskFacade.getVariable(processInstanceId, "deviceName"));
        String commandKey = asString(bpmTaskFacade.getVariable(processInstanceId, "commandKey"));
        String commandType = asString(bpmTaskFacade.getVariable(processInstanceId, "commandType"));

        boolean legacyInvocation = request == null;
        ApprovalActionRequest effectiveRequest = legacyInvocation ? new ApprovalActionRequest() : request;
        ApprovalAction action = effectiveRequest.getAction() == null ? ApprovalAction.APPROVE
                : effectiveRequest.getAction();
        effectiveRequest.setAction(action);
        Map<String, Object> processVariables = bpmTaskFacade.getVariables(processInstanceId);
        ApprovalOpinionValidator.validate(effectiveRequest, resolveOpinionForm(task), processVariables);
        if (action == ApprovalAction.RETURN) {
            if (effectiveRequest.getReturnTargetNodeId() == null
                    || effectiveRequest.getReturnTargetNodeId().isBlank()) {
                throw new BaseException(com.sw.ck.bpm.api.exception.BpmErrorCode.APPROVAL_RETURN_TARGET_INVALID);
            }
            bpmTaskFacade.returnTask(taskId, effectiveRequest.getReturnTargetNodeId());
            if (participantSnapshotRecorder != null) {
                participantSnapshotRecorder.settle(processInstanceId, task.getTaskDefinitionKey(),
                        task.getTaskId(), String.valueOf(loginUser.getUserId()), action.name(),
                        loginUser.getTenantId());
            }
            recordAction(task, loginUser, effectiveRequest, action, "RETURNED", processVariables);
            publishProcessEvent(processInstanceId, loginUser, BpmNotifyTrigger.PROCESS_RETURNED);
            return R.ok();
        }

        Map<String, Object> variables = legacyInvocation ? null : new java.util.HashMap<>();
        if (variables != null) {
            variables.put("outcome", action == ApprovalAction.REJECT ? "REJECTED" : "APPROVED");
        }
        try {
            if (assigned) bpmTaskFacade.complete(taskId, variables);
            else bpmTaskFacade.completeAsUser(taskId, String.valueOf(loginUser.getUserId()),
                    variables == null ? Map.of() : variables);
        } catch (RuntimeException e) {
            BaseException branchFailure = findBaseException(e,
                    com.sw.ck.bpm.api.exception.BpmErrorCode.BRANCH_EVALUATION_FAILED.getCode());
            if (branchFailure != null) {
                bpmInstanceService.updateStatus(processInstanceId, InstanceStatusEnum.FAILED.getCode());
                log.warn("分支条件求值失败，实例进入 FAILED: processInstanceId={}, taskId={}",
                        processInstanceId, taskId);
                return R.fail(branchFailure.getCode(), branchFailure.getMessage());
            }
            throw e;
        }
        // 普通审批的 REJECT 是流程终态；会签子任务的 REJECT 只是该参与人的
        // 独立意见，必须交给 CONSENSUS completionCondition 按 ANY/ALL/RATIO
        // 结算，不能被这里的通用终止分支提前截断。
        if (action == ApprovalAction.REJECT && !isConsensusTask(task)) {
            // 驳回是流程终态动作；没有显式驳回分支时不能让线性流程继续创建后续待办。
            bpmTaskFacade.terminateProcess(processInstanceId, "REJECTED");
        }
        if (participantSnapshotRecorder != null) {
            participantSnapshotRecorder.settle(processInstanceId, task.getTaskDefinitionKey(),
                    task.getTaskId(), String.valueOf(loginUser.getUserId()), action.name(),
                    loginUser.getTenantId());
        }
        recordAction(task, loginUser, effectiveRequest, action,
                action == ApprovalAction.REJECT ? "REJECTED" : "APPROVED", processVariables);
        log.info("审批已完成: taskId={}, processInstanceId={}, userId={}",
                taskId, processInstanceId, loginUser.getUserId());

        // 4. 检测流程是否结束（经 Facade，不直接查 RuntimeService）
        if (!bpmTaskFacade.isProcessActive(processInstanceId)) {
            String terminalStatus = action == ApprovalAction.REJECT
                    ? InstanceStatusEnum.REJECTED.getCode()
                    : InstanceStatusEnum.APPROVED.getCode();
            bpmInstanceService.updateStatus(processInstanceId, terminalStatus);
            log.info("流程已结束，实例状态更新为 {}: processInstanceId={}",
                    terminalStatus, processInstanceId);

            // — 发布审批结果通知事件 —
            publishProcessEvent(processInstanceId, loginUser,
                    action == ApprovalAction.REJECT ? BpmNotifyTrigger.PROCESS_REJECTED
                            : BpmNotifyTrigger.PROCESS_APPROVED);

            // — 审批结果驱动设备：流程变量携带 productId/deviceName/commandKey 时发布设备命令事件 —
            if (productId != null && deviceName != null && commandKey != null) {
                if (commandType == null) {
                    commandType = "PROPERTY";
                }
                domainEventPublisher.publish(new BpmDeviceCommandEvent(
                        processInstanceId, productId, deviceName,
                        commandKey, commandType,
                        loginUser.getTenantId(), loginUser.getUserId()));
                log.info("设备命令事件已发布: processInstanceId={}, productId={}, deviceName={}, commandKey={}",
                        processInstanceId, productId, deviceName, commandKey);
            }
        }

        return R.ok();
    }

    /**
     * 流程变量取值转字符串（null 或空白返回 null）。
     */
    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private BaseException findBaseException(Throwable error, int code) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof BaseException baseException && baseException.getCode() == code) {
                return baseException;
            }
            current = current.getCause();
        }
        return null;
    }

    /**
     * 流程结束时发布 PROCESS_APPROVED 通知事件。
     * <p>
     * 收件人为流程发起人（instance.initiatorId），
     * actorUserId 为当前审批人（用于异步线程还原 LoginUserHolder）。
     * </p>
     */
    private void publishApprovedEvent(String processInstanceId, LoginUser loginUser) {
        publishProcessEvent(processInstanceId, loginUser, BpmNotifyTrigger.PROCESS_APPROVED);
    }

    private void publishProcessEvent(String processInstanceId, LoginUser loginUser,
                                     BpmNotifyTrigger trigger) {
        BpmInstance instance = bpmInstanceService
                .findByProcessInstanceId(processInstanceId)
                .orElse(null);
        if (instance == null) {
            log.warn("流程实例记录不存在: processInstanceId={}，跳过 {} 通知",
                    processInstanceId, trigger);
            return;
        }

        BpmNotifyEvent event = new BpmNotifyEvent(
                trigger,
                instance.getInitiatorId(),
                loginUser.getTenantId(),
                loginUser.getUserId(),
                processInstanceId
        );
        domainEventPublisher.publish(event);
        log.debug("流程结果事件已发布: trigger={}, processInstanceId={}, initiatorId={}",
                trigger, processInstanceId, instance.getInitiatorId());
    }

    private void recordAction(BpmTaskDTO task, LoginUser loginUser,
                              ApprovalActionRequest request, ApprovalAction action,
                              String settlementStatus, Map<String, Object> processVariables) {
        if (approvalActionService == null) return;
        ApprovalActionRecord record = new ApprovalActionRecord();
        record.setProcessInstanceId(task.getProcessInstanceId());
        record.setNodeKey(task.getTaskDefinitionKey() == null
                ? task.getTaskId() : task.getTaskDefinitionKey());
        record.setTaskId(task.getTaskId());
        record.setActorId(loginUser.getUserId());
        record.setAction(action.name());
        record.setOpinionFormId(request.getOpinionFormId());
        record.setOpinionFormVersion(request.getOpinionFormVersion());
        Map<String, Object> opinionData = request.getOpinionData() == null
                ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(request.getOpinionData());
        if (request.getComment() != null && !request.getComment().isBlank()) {
            opinionData.putIfAbsent("comment", request.getComment());
        }
        try {
            record.setOpinionData(objectMapper == null ? "{}" : objectMapper.writeValueAsString(opinionData));
            Map<String, Object> initialization = new java.util.LinkedHashMap<>();
            initialization.put("source", "processVariables.formData");
            Object formData = processVariables == null ? null : processVariables.get("formData");
            if (formData instanceof Map<?, ?> values) {
                initialization.put("sourceFields", values.keySet().stream().map(String::valueOf).toList());
            }
            record.setInitializationSummary(objectMapper == null ? "{}"
                    : objectMapper.writeValueAsString(initialization));
        } catch (Exception e) {
            throw new BaseException(com.sw.ck.bpm.api.exception.BpmErrorCode.APPROVAL_OPINION_INVALID);
        }
        record.setSettlementStatus(settlementStatus);
        record.setTenantId(loginUser.getTenantId());
        approvalActionService.save(record);
    }

    private Map<String, Object> resolveOpinionForm(BpmTaskDTO task) {
        if (bpmProcessDefService == null || objectMapper == null
                || task.getProcessDefinitionKey() == null || task.getTaskDefinitionKey() == null) {
            return Map.of();
        }
        try {
            BpmProcessDef definition = bpmProcessDefService
                    .findByProcessKey(task.getProcessDefinitionKey());
            if (definition == null || definition.getGraphJson() == null) return Map.of();
            ProcessGraph graph = objectMapper.readValue(definition.getGraphJson(), ProcessGraph.class);
            if (graph.getElements() == null) return Map.of();
            return graph.getElements().stream()
                    .filter(element -> "node".equals(element.getKind())
                            && task.getTaskDefinitionKey().equals(element.getId()))
                    .map(GraphElement::getConfig)
                    .filter(java.util.Objects::nonNull)
                    .map(config -> config.get("opinionForm"))
                    .filter(Map.class::isInstance)
                    .map(value -> (Map<String, Object>) value)
                    .findFirst().orElse(Map.of());
        } catch (Exception e) {
            throw new BaseException(com.sw.ck.bpm.api.exception.BpmErrorCode.APPROVAL_OPINION_INVALID);
        }
    }

    private boolean isConsensusTask(BpmTaskDTO task) {
        if (task == null || bpmProcessDefService == null || objectMapper == null
                || task.getProcessDefinitionKey() == null || task.getTaskDefinitionKey() == null) {
            return false;
        }
        try {
            BpmProcessDef definition = bpmProcessDefService
                    .findByProcessKey(task.getProcessDefinitionKey());
            if (definition == null || definition.getGraphJson() == null) return false;
            ProcessGraph graph = objectMapper.readValue(definition.getGraphJson(), ProcessGraph.class);
            if (graph.getElements() == null) return false;
            return graph.getElements().stream()
                    .filter(element -> "node".equals(element.getKind())
                            && task.getTaskDefinitionKey().equals(element.getId()))
                    .anyMatch(element -> "CONSENSUS".equalsIgnoreCase(element.getType()));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 按 ID 批量解析用户展示名；查不到的 ID 返回 null，不阻断查询。
     */
    private Map<Long, String> resolveUserNames(java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        try {
            return userQueryFacade.getUserDisplayNames(ids);
        } catch (Exception e) {
            log.warn("用户展示名批量查询失败，回退为 null: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 驳回审批。
     *
     * @param taskId Flowable task ID
     * @return 操作成功
     * @throws BaseException 任务不存在 / 越权时抛出
     */
    /** 兼容既有 Java 调用方；HTTP 映射使用带 body 的重载。 */
    public R<Void> reject(String taskId) {
        return reject(taskId, null);
    }

    @Transactional
    @PostMapping("/{taskId}/reject")
    public R<Void> reject(@PathVariable String taskId,
                          @RequestBody(required = false) ApprovalActionRequest request) {
        ApprovalActionRequest actionRequest = request == null ? new ApprovalActionRequest() : request;
        actionRequest.setAction(ApprovalAction.REJECT);
        return handleAction(taskId, actionRequest);
    }

    @Transactional
    @PostMapping("/{taskId}/return")
    public R<Void> returnTask(@PathVariable String taskId,
                              @RequestBody ApprovalActionRequest request) {
        ApprovalActionRequest actionRequest = request == null ? new ApprovalActionRequest() : request;
        actionRequest.setAction(ApprovalAction.RETURN);
        return handleAction(taskId, actionRequest);
    }

    // ==================== 内部方法 ====================

    /**
     * 任务详情。
     * <p>
     * 返回任务基本信息、发起人、流程变量等完整信息。
     * </p>
     *
     * @param taskId Flowable task ID
     * @return 任务详情
     * @throws BaseException 任务不存在时抛出
     */
    @GetMapping("/{taskId}")
    public R<TaskDetailRespDTO> detail(@PathVariable String taskId) {
        BpmTaskDTO task = bpmTaskFacade.getTask(taskId);
        if (task == null) {
            throw new BaseException(CommonErrorCode.NOT_FOUND.getCode(), "任务不存在");
        }

        TaskDetailRespDTO dto = new TaskDetailRespDTO();
        dto.setTaskId(task.getTaskId());
        dto.setTaskName(task.getName());
        dto.setProcessInstanceId(task.getProcessInstanceId());
        dto.setProcessDefinitionKey(task.getProcessDefinitionKey());

        // processName 富化
        if (task.getProcessDefinitionKey() != null) {
            BpmProcessDef processDef = bpmProcessDefService.findByProcessKey(task.getProcessDefinitionKey());
            if (processDef != null) {
                dto.setProcessName(processDef.getName());
            }
        }

        // formKey 从流程变量获取
        String formKey = bpmTaskFacade.getVariable(task.getProcessInstanceId(), "formKey");
        dto.setFormKey(formKey);

        dto.setBusinessKey(task.getBusinessKey());
        dto.setAssignee(task.getAssignee());

        // 发起人
        bpmInstanceService.findByProcessInstanceId(task.getProcessInstanceId())
                .ifPresent(instance -> {
                    dto.setInitiatorId(instance.getInitiatorId());
                    dto.setInitiatorName(resolveUserNames(
                            instance.getInitiatorId() == null
                                    ? java.util.Set.of()
                                    : java.util.Set.of(instance.getInitiatorId()))
                            .get(instance.getInitiatorId()));
                });

        // 任务创建时间
        if (task.getCreateTime() != null) {
            dto.setCreateTime(LocalDateTime.ofInstant(
                    task.getCreateTime().toInstant(), ZoneId.systemDefault()));
        }

        // 流程变量
        Map<String, Object> variables = bpmTaskFacade.getVariables(task.getProcessInstanceId());
        dto.setProcessVariables(variables);
        dto.setOpinionForm(resolveOpinionForm(task));

        if (task.getAssignee() != null && task.getAssignee().matches("\\d+")) {
            dto.setAssigneeName(resolveUserNames(java.util.Set.of(Long.valueOf(task.getAssignee())))
                    .get(Long.valueOf(task.getAssignee())));
        }

        log.debug("任务详情查询: taskId={}, processInstanceId={}", taskId, task.getProcessInstanceId());

        // 审批历史
        List<BpmTaskDTO> historyTasks = bpmTaskFacade.queryHistoryByProcessInstance(task.getProcessInstanceId());
        List<ApprovalHistoryItemDTO> history = new java.util.ArrayList<>();
        for (BpmTaskDTO h : historyTasks) {
            ApprovalHistoryItemDTO item = new ApprovalHistoryItemDTO();
            item.setTaskId(h.getTaskId());
            item.setTaskName(h.getName());
            item.setNodeKey(h.getTaskDefinitionKey());
            item.setAssignee(h.getAssignee());
            if (h.getCreateTime() != null) {
                item.setCreateTime(LocalDateTime.ofInstant(
                        h.getCreateTime().toInstant(), ZoneId.systemDefault()));
            }
            if (h.getEndTime() != null) {
                item.setEndTime(LocalDateTime.ofInstant(
                        h.getEndTime().toInstant(), ZoneId.systemDefault()));
            }
            history.add(item);
        }
        if (approvalActionService != null) {
            Map<String, ApprovalActionRecord> actions = approvalActionService
                    .findByProcessInstanceId(task.getProcessInstanceId()).stream()
                    .collect(Collectors.toMap(ApprovalActionRecord::getTaskId,
                            java.util.function.Function.identity(), (left, right) -> left));
            for (ApprovalHistoryItemDTO item : history) {
                ApprovalActionRecord action = actions.get(item.getTaskId());
                if (action == null) continue;
                item.setAction(action.getAction());
                item.setApprovalResult("APPROVE".equals(action.getAction()) ? "APPROVED"
                        : "REJECT".equals(action.getAction()) ? "REJECTED" : null);
                item.setOpinionFormId(action.getOpinionFormId());
                item.setOpinionFormVersion(action.getOpinionFormVersion());
                if (action.getOpinionData() != null && objectMapper != null) {
                    try {
                        item.setOpinionData(objectMapper.readValue(action.getOpinionData(), Map.class));
                    } catch (Exception ignored) {
                        item.setOpinionData(Map.of());
                    }
                }
            }
        }
        // 审批人展示名富化（可读身份回显；查询失败不阻断详情）
        Map<Long, String> historyNames = resolveUserNames(historyTasks.stream()
                .map(BpmTaskDTO::getAssignee)
                .filter(a -> a != null && a.matches("\\d+"))
                .map(Long::valueOf)
                .collect(Collectors.toSet()));
        for (ApprovalHistoryItemDTO item : history) {
            if (item.getAssignee() != null && item.getAssignee().matches("\\d+")) {
                item.setAssigneeName(historyNames.get(Long.valueOf(item.getAssignee())));
            }
        }
        dto.setApprovalHistory(history);

        return R.ok(dto);
    }

    /**
     * 当前用户已办列表（分页）。
     */
    @GetMapping("/processed")
    public R<PageResult<ProcessedTaskRespDTO>> processed(PageParam pageParam) {
        LoginUser loginUser = LoginUserHolder.get();
        String tenantId = String.valueOf(loginUser.getTenantId());
        String assignee = String.valueOf(loginUser.getUserId());

        long offset = (pageParam.getPageNum() - 1) * pageParam.getPageSize();
        int limit = (int) pageParam.getPageSize();

        List<BpmTaskDTO> tasks = bpmTaskFacade.queryProcessedPage(tenantId, assignee, (int) offset, limit);
        long total = bpmTaskFacade.countProcessed(tenantId, assignee);

        List<ProcessedTaskRespDTO> dtos = tasks.stream()
                .map(this::toProcessedTaskDTO)
                .collect(Collectors.toList());

        log.debug("已办查询: tenantId={}, assignee={}, total={}, pageNum={}, pageSize={}",
                tenantId, assignee, total, pageParam.getPageNum(), pageParam.getPageSize());

        PageResult<ProcessedTaskRespDTO> pageResult = new PageResult<>();
        pageResult.setRecords(dtos);
        pageResult.setTotal(total);
        pageResult.setPageNum(pageParam.getPageNum());
        pageResult.setPageSize(pageParam.getPageSize());

        return R.ok(pageResult);
    }

    /**
     * 将 BpmTaskDTO(Date) 富化为 TodoTaskRespDTO(LocalDateTime)。
     * <p>
     * 时间转换：使用系统默认时区 {@code LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault())}，
     * 不静默丢精度。
     * formKey 和 processName 从流程变量/流程定义获取（经 Facade/Service），
     * businessKey 由 Facade 直接返回。
     * </p>
     */
    private TodoTaskRespDTO toTodoTaskDTO(BpmTaskDTO task) {
        TodoTaskRespDTO dto = new TodoTaskRespDTO();
        dto.setTaskId(task.getTaskId());
        dto.setProcessInstanceId(task.getProcessInstanceId());

        // BpmTaskDTO.createTime: Date → LocalDateTime（显式时区转换）
        if (task.getCreateTime() != null) {
            dto.setCreateTime(LocalDateTime.ofInstant(
                    task.getCreateTime().toInstant(), ZoneId.systemDefault()));
        }

        // businessKey 直接取自 BpmTaskDTO（Facade 层已填充）
        dto.setBusinessKey(task.getBusinessKey());

        // formKey 从流程变量获取（经 Facade）
        String formKey = bpmTaskFacade.getVariable(
                task.getProcessInstanceId(), "formKey");
        dto.setFormKey(formKey);

        // processName 富化（经 BpmProcessDefService）
        if (task.getProcessDefinitionKey() != null) {
            BpmProcessDef processDef = bpmProcessDefService.findByProcessKey(task.getProcessDefinitionKey());
            if (processDef != null) {
                dto.setProcessName(processDef.getName());
            }
        }

        return dto;
    }

    /**
     * 将 BpmTaskDTO 富化为 ProcessedTaskRespDTO。
     */
    private ProcessedTaskRespDTO toProcessedTaskDTO(BpmTaskDTO task) {
        ProcessedTaskRespDTO dto = new ProcessedTaskRespDTO();
        dto.setTaskId(task.getTaskId());
        dto.setTaskName(task.getName());
        dto.setProcessInstanceId(task.getProcessInstanceId());

        if (task.getCreateTime() != null) {
            dto.setCreateTime(LocalDateTime.ofInstant(
                    task.getCreateTime().toInstant(), ZoneId.systemDefault()));
        }
        if (task.getEndTime() != null) {
            dto.setEndTime(LocalDateTime.ofInstant(
                    task.getEndTime().toInstant(), ZoneId.systemDefault()));
        }

        String formKey = bpmTaskFacade.getVariable(
                task.getProcessInstanceId(), "formKey");
        dto.setFormKey(formKey);
        dto.setBusinessKey(bpmTaskFacade.getBusinessKey(task.getProcessInstanceId()));

        if (task.getProcessDefinitionKey() != null) {
            BpmProcessDef processDef = bpmProcessDefService.findByProcessKey(task.getProcessDefinitionKey());
            if (processDef != null) {
                dto.setProcessName(processDef.getName());
            }
        }

        return dto;
    }
}
