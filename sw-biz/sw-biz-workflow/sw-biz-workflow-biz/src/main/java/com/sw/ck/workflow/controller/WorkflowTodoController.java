package com.sw.ck.workflow.controller;

import com.sw.ck.common.event.DomainEventPublisher;
import com.sw.ck.common.exception.BaseException;
import com.sw.ck.common.exception.CommonErrorCode;
import com.sw.ck.common.response.R;
import com.sw.ck.security.holder.LoginUser;
import com.sw.ck.security.holder.LoginUserHolder;
import com.sw.ck.workflow.api.event.WorkflowNotifyEvent;
import com.sw.ck.workflow.api.event.WorkflowNotifyTrigger;
import com.sw.ck.workflow.dto.TodoTaskRespDTO;
import com.sw.ck.workflow.entity.InstanceStatusEnum;
import com.sw.ck.workflow.entity.WorkflowInstance;
import com.sw.ck.workflow.service.WorkflowInstanceService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
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
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 待办中心控制器（M04 第三环第 5 步）。
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
 * {@code complete} 成功后查 {@code RuntimeService} 该 processInstanceId 是否
 * 无活动实例（单节点 complete 后流程即结束），若是则更新
 * {@code sw_workflow_instance.status = APPROVED}。
 *
 * <h3>待办 DTO 字段来源</h3>
 * <ul>
 *   <li>{@code businessKey} — 从 {@link ProcessInstance#getBusinessKey()} 获取</li>
 *   <li>{@code formKey} — 从 Flowable 流程变量 {@code formKey} 获取</li>
 * </ul>
 */
@RestController
@RequestMapping("/workflow/tasks")
public class WorkflowTodoController {

    private static final Logger log = LoggerFactory.getLogger(WorkflowTodoController.class);

    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final WorkflowInstanceService workflowInstanceService;
    private final DomainEventPublisher domainEventPublisher;

    public WorkflowTodoController(TaskService taskService,
                                  RuntimeService runtimeService,
                                  WorkflowInstanceService workflowInstanceService,
                                  DomainEventPublisher domainEventPublisher) {
        this.taskService = taskService;
        this.runtimeService = runtimeService;
        this.workflowInstanceService = workflowInstanceService;
        this.domainEventPublisher = domainEventPublisher;
    }

    /**
     * 当前用户待办列表。
     * <p>
     * 按 {@code taskTenantId + taskAssignee} 双条件查询 Flowable ACT_RU_TASK，
     * 结果包含 formKey/businessKey 等业务信息（从 Flowable 流程实例/变量获取）。
     * </p>
     *
     * @return 待办任务列表（可能为空）
     */
    @GetMapping("/todo")
    public R<List<TodoTaskRespDTO>> todo() {
        LoginUser loginUser = LoginUserHolder.get();
        String tenantId = String.valueOf(loginUser.getTenantId());
        String assignee = String.valueOf(loginUser.getUserId());

        List<Task> tasks = taskService.createTaskQuery()
                .taskTenantId(tenantId)
                .taskAssignee(assignee)
                .list();

        List<TodoTaskRespDTO> dtos = tasks.stream()
                .map(this::toTodoTaskDTO)
                .collect(Collectors.toList());

        log.debug("待办查询: tenantId={}, assignee={}, count={}",
                tenantId, assignee, dtos.size());

        return R.ok(dtos);
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
    /**
     * {@code @Transactional} 确保 status 更新与事件发布在同一事务内，
     * {@code WorkflowNotifyListener} 的 {@code @TransactionalEventListener(AFTER_COMMIT)}
     * 在此事务提交后才触发。
     */
    @Transactional
    @PostMapping("/{taskId}/complete")
    public R<Void> complete(@PathVariable String taskId) {
        LoginUser loginUser = LoginUserHolder.get();

        // 1. 查询 task
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new BaseException(CommonErrorCode.NOT_FOUND.getCode(), "任务不存在");
        }

        // 2. 越权校验：租户
        if (!String.valueOf(loginUser.getTenantId()).equals(task.getTenantId())) {
            log.warn("越权拒绝（租户不匹配）: taskId={}, taskTenantId={}, currentTenantId={}",
                    taskId, task.getTenantId(), loginUser.getTenantId());
            throw new BaseException(CommonErrorCode.FORBIDDEN.getCode(), "无权处理该任务");
        }

        // 3. 越权校验：审批人
        if (!String.valueOf(loginUser.getUserId()).equals(task.getAssignee())) {
            log.warn("越权拒绝（审批人不匹配）: taskId={}, taskAssignee={}, currentUserId={}",
                    taskId, task.getAssignee(), loginUser.getUserId());
            throw new BaseException(CommonErrorCode.FORBIDDEN.getCode(), "无权处理该任务");
        }

        String processInstanceId = task.getProcessInstanceId();

        // 4. 完成审批
        taskService.complete(taskId);
        log.info("审批已完成: taskId={}, processInstanceId={}, userId={}",
                taskId, processInstanceId, loginUser.getUserId());

        // 5. 检测流程是否结束（单节点 complete 后流程即结束）
        long activeCount = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .count();
        if (activeCount == 0) {
            workflowInstanceService.updateStatus(
                    processInstanceId, InstanceStatusEnum.APPROVED.getCode());
            log.info("流程已结束，实例状态更新为 APPROVED: processInstanceId={}",
                    processInstanceId);

            // — M05 Step 2：发布 PROCESS_APPROVED 通知事件 —
            // 在同一事务内发布，AFTER_COMMIT 提交后才触发 listener
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
        WorkflowInstance instance = workflowInstanceService
                .findByProcessInstanceId(processInstanceId)
                .orElse(null);
        if (instance == null) {
            log.warn("流程实例记录不存在: processInstanceId={}，跳过 PROCESS_APPROVED 通知",
                    processInstanceId);
            return;
        }

        WorkflowNotifyEvent event = new WorkflowNotifyEvent(
                WorkflowNotifyTrigger.PROCESS_APPROVED,
                instance.getInitiatorId(),
                loginUser.getTenantId(),
                loginUser.getUserId(),
                processInstanceId
        );
        domainEventPublisher.publish(event);
        log.debug("PROCESS_APPROVED 事件已发布: processInstanceId={}, initiatorId={}",
                processInstanceId, instance.getInitiatorId());
    }

    // ==================== 内部方法 ====================

    /**
     * 将 Flowable Task 转换为待办 DTO。
     * <p>
     * formKey 从流程变量获取，businessKey 从 ProcessInstance 获取。
     * 若 WorkflowInstance 已存在也一并携带（当前 skeleton 阶段始终存在）。
     * </p>
     */
    private TodoTaskRespDTO toTodoTaskDTO(Task task) {
        TodoTaskRespDTO dto = new TodoTaskRespDTO();
        dto.setTaskId(task.getId());
        dto.setProcessInstanceId(task.getProcessInstanceId());

        // 任务创建时间
        if (task.getCreateTime() != null) {
            dto.setCreateTime(LocalDateTime.ofInstant(
                    task.getCreateTime().toInstant(), ZoneId.systemDefault()));
        }

        // businessKey 从 ProcessInstance 获取
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(task.getProcessInstanceId())
                .singleResult();
        if (pi != null) {
            dto.setBusinessKey(pi.getBusinessKey());
        }

        // formKey 从流程变量获取
        Object formKeyVal = runtimeService.getVariable(
                task.getProcessInstanceId(), "formKey");
        if (formKeyVal instanceof String) {
            dto.setFormKey((String) formKeyVal);
        }

        return dto;
    }
}
