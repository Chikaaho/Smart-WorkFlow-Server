package com.sw.ck.bpm.process.controller;

import com.sw.ck.bpm.api.dto.BpmTaskDTO;
import com.sw.ck.bpm.api.event.BpmNotifyEvent;
import com.sw.ck.bpm.api.event.BpmNotifyTrigger;
import com.sw.ck.bpm.api.facade.BpmTaskFacade;
import com.sw.ck.bpm.process.dto.ApprovalHistoryItemDTO;
import com.sw.ck.bpm.process.dto.ProcessedTaskRespDTO;
import com.sw.ck.bpm.process.dto.TaskDetailRespDTO;
import com.sw.ck.bpm.process.dto.TodoTaskRespDTO;
import com.sw.ck.bpm.process.entity.BpmInstance;
import com.sw.ck.bpm.process.entity.BpmProcessDef;
import com.sw.ck.bpm.process.entity.InstanceStatusEnum;
import com.sw.ck.bpm.process.service.BpmInstanceService;
import com.sw.ck.bpm.process.service.BpmProcessDefService;
import com.sw.ck.common.event.DomainEventPublisher;
import com.sw.ck.common.exception.BaseException;
import com.sw.ck.common.exception.CommonErrorCode;
import com.sw.ck.common.page.PageParam;
import com.sw.ck.common.page.PageResult;
import com.sw.ck.common.response.R;
import com.sw.ck.security.holder.LoginUser;
import com.sw.ck.security.holder.LoginUserHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
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

    public BpmTodoController(BpmTaskFacade bpmTaskFacade,
                             BpmInstanceService bpmInstanceService,
                             BpmProcessDefService bpmProcessDefService,
                             DomainEventPublisher domainEventPublisher) {
        this.bpmTaskFacade = bpmTaskFacade;
        this.bpmInstanceService = bpmInstanceService;
        this.bpmProcessDefService = bpmProcessDefService;
        this.domainEventPublisher = domainEventPublisher;
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
    @Transactional
    @PostMapping("/{taskId}/complete")
    public R<Void> complete(@PathVariable String taskId) {
        LoginUser loginUser = LoginUserHolder.get();

        // 1. 查询 task（经 Facade 包装，无 Flowable 泄漏）
        BpmTaskDTO task = bpmTaskFacade.getTask(taskId);
        if (task == null) {
            throw new BaseException(CommonErrorCode.NOT_FOUND.getCode(), "任务不存在");
        }

        // 2. 越权校验：审批人
        if (!String.valueOf(loginUser.getUserId()).equals(task.getAssignee())) {
            log.warn("越权拒绝（审批人不匹配）: taskId={}, taskAssignee={}, currentUserId={}",
                    taskId, task.getAssignee(), loginUser.getUserId());
            throw new BaseException(CommonErrorCode.FORBIDDEN.getCode(), "无权处理该任务");
        }

        String processInstanceId = task.getProcessInstanceId();

        // 3. 完成审批（经 Facade）
        bpmTaskFacade.complete(taskId, null);
        log.info("审批已完成: taskId={}, processInstanceId={}, userId={}",
                taskId, processInstanceId, loginUser.getUserId());

        // 4. 检测流程是否结束（经 Facade，不直接查 RuntimeService）
        if (!bpmTaskFacade.isProcessActive(processInstanceId)) {
            bpmInstanceService.updateStatus(
                    processInstanceId, InstanceStatusEnum.APPROVED.getCode());
            log.info("流程已结束，实例状态更新为 APPROVED: processInstanceId={}",
                    processInstanceId);

            // — 发布 PROCESS_APPROVED 通知事件 —
            publishApprovedEvent(processInstanceId, loginUser);
        }

        return R.ok();
    }

    /**
     * 流程结束时发布 PROCESS_APPROVED 通知事件。
     * <p>
     * 收件人为流程发起人（instance.initiatorId），
     * actorUserId 为当前审批人（用于异步线程还原 LoginUserHolder）。
     * </p>
     */
    private void publishApprovedEvent(String processInstanceId, LoginUser loginUser) {
        BpmInstance instance = bpmInstanceService
                .findByProcessInstanceId(processInstanceId)
                .orElse(null);
        if (instance == null) {
            log.warn("流程实例记录不存在: processInstanceId={}，跳过 PROCESS_APPROVED 通知",
                    processInstanceId);
            return;
        }

        BpmNotifyEvent event = new BpmNotifyEvent(
                BpmNotifyTrigger.PROCESS_APPROVED,
                instance.getInitiatorId(),
                loginUser.getTenantId(),
                loginUser.getUserId(),
                processInstanceId
        );
        domainEventPublisher.publish(event);
        log.debug("PROCESS_APPROVED 事件已发布: processInstanceId={}, initiatorId={}",
                processInstanceId, instance.getInitiatorId());
    }

    /**
     * 驳回审批。
     *
     * @param taskId Flowable task ID
     * @return 操作成功
     * @throws BaseException 任务不存在 / 越权时抛出
     */
    @Transactional
    @PostMapping("/{taskId}/reject")
    public R<Void> reject(@PathVariable String taskId) {
        LoginUser loginUser = LoginUserHolder.get();

        BpmTaskDTO task = bpmTaskFacade.getTask(taskId);
        if (task == null) {
            throw new BaseException(CommonErrorCode.NOT_FOUND.getCode(), "任务不存在");
        }

        if (!String.valueOf(loginUser.getUserId()).equals(task.getAssignee())) {
            log.warn("越权拒绝（审批人不匹配）: taskId={}, taskAssignee={}, currentUserId={}",
                    taskId, task.getAssignee(), loginUser.getUserId());
            throw new BaseException(CommonErrorCode.FORBIDDEN.getCode(), "无权处理该任务");
        }

        String processInstanceId = task.getProcessInstanceId();

        Map<String, Object> variables = new java.util.HashMap<>();
        variables.put("outcome", "REJECTED");
        bpmTaskFacade.complete(taskId, variables);
        log.info("审批已驳回: taskId={}, processInstanceId={}, userId={}",
                taskId, processInstanceId, loginUser.getUserId());

        if (!bpmTaskFacade.isProcessActive(processInstanceId)) {
            bpmInstanceService.updateStatus(
                    processInstanceId, InstanceStatusEnum.REJECTED.getCode());
            log.info("流程已结束（驳回），实例状态更新为 REJECTED: processInstanceId={}",
                    processInstanceId);
        }

        return R.ok();
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
                .ifPresent(instance -> dto.setInitiatorId(instance.getInitiatorId()));

        // 任务创建时间
        if (task.getCreateTime() != null) {
            dto.setCreateTime(LocalDateTime.ofInstant(
                    task.getCreateTime().toInstant(), ZoneId.systemDefault()));
        }

        // 流程变量
        Map<String, Object> variables = bpmTaskFacade.getVariables(task.getProcessInstanceId());
        dto.setProcessVariables(variables);

        log.debug("任务详情查询: taskId={}, processInstanceId={}", taskId, task.getProcessInstanceId());

        // 审批历史
        List<BpmTaskDTO> historyTasks = bpmTaskFacade.queryHistoryByProcessInstance(task.getProcessInstanceId());
        List<ApprovalHistoryItemDTO> history = new java.util.ArrayList<>();
        for (BpmTaskDTO h : historyTasks) {
            ApprovalHistoryItemDTO item = new ApprovalHistoryItemDTO();
            item.setTaskId(h.getTaskId());
            item.setTaskName(h.getName());
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

        if (task.getProcessDefinitionKey() != null) {
            BpmProcessDef processDef = bpmProcessDefService.findByProcessKey(task.getProcessDefinitionKey());
            if (processDef != null) {
                dto.setProcessName(processDef.getName());
            }
        }

        return dto;
    }
}
