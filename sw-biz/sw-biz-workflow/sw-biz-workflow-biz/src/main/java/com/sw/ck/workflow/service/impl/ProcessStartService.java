package com.sw.ck.workflow.service.impl;

import com.sw.ck.common.event.DomainEventPublisher;
import com.sw.ck.workflow.api.dto.ApproverContext;
import com.sw.ck.workflow.api.event.WorkflowNotifyEvent;
import com.sw.ck.workflow.api.event.WorkflowNotifyTrigger;
import com.sw.ck.workflow.api.spi.ApproverResolver;
import com.sw.ck.workflow.dto.StartCommand;
import com.sw.ck.workflow.entity.InstanceStatusEnum;
import com.sw.ck.workflow.entity.WorkflowFormBinding;
import com.sw.ck.workflow.entity.WorkflowInstance;
import com.sw.ck.workflow.service.WorkflowFormBindingService;
import com.sw.ck.workflow.service.WorkflowInstanceService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程发起唯一入口。
 * <p>
 * 封装表单绑定查询 → 审批人解析 → Flowable 发起 → 实例落库 四个步骤，
 * 由 {@code @Transactional} 保证 Flowable 操作 + 实例插入的原子性。
 * </p>
 *
 * <p>
 * 调用方：
 * <ul>
 *   <li>{@link com.sw.ck.workflow.listener.FormSubmittedEventListener}
 *       — 表单提交事件 {@code AFTER_COMMIT} 后异步触发</li>
 *   <li>后续 {@code ScheduledFlowTriggerEvent} 监听器 — 定时任务 FLOW 类型发起</li>
 * </ul>
 * </p>
 *
 * <p>
 * 事务边界：{@code @Transactional} 仅包围 workflow 侧的操作（Flowable + 实例落库），
 * 表单提交的事务已在 {@code AFTER_COMMIT} 时独立提交。流程发起失败不回滚表单数据，
 * 这是预期的解耦设计。
 * </p>
 */
@Service
public class ProcessStartService {

    private static final Logger log = LoggerFactory.getLogger(ProcessStartService.class);

    private final WorkflowFormBindingService bindingService;
    private final ApproverResolver approverResolver;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final WorkflowInstanceService workflowInstanceService;
    private final DomainEventPublisher domainEventPublisher;

    public ProcessStartService(WorkflowFormBindingService bindingService,
                                ApproverResolver approverResolver,
                                RuntimeService runtimeService,
                                TaskService taskService,
                                WorkflowInstanceService workflowInstanceService,
                                DomainEventPublisher domainEventPublisher) {
        this.bindingService = bindingService;
        this.approverResolver = approverResolver;
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.workflowInstanceService = workflowInstanceService;
        this.domainEventPublisher = domainEventPublisher;
    }

    /**
     * 发起流程实例。
     * <ol>
     *   <li>查启用绑定 — 无绑定则 info 日志 return（no-op，不是每个表单都走流程）</li>
     *   <li>解析审批人 — 经 {@link ApproverResolver} 获取 approve 变量值</li>
     *   <li>Flowable 发起 — {@code startProcessInstanceByKeyAndTenantId}，只有 id 引用变量</li>
     *   <li>落实例 — {@code sw_workflow_instance} 写入 RUNNING 状态（基列靠拦截器自动注入）</li>
     * </ol>
     *
     * @param cmd 发起命令，不可为空
     */
    @Transactional
    public void start(StartCommand cmd) {
        // 1. 查启用绑定
        List<WorkflowFormBinding> bindings = bindingService.findActiveByFormKey(cmd.getFormKey());
        if (bindings.isEmpty()) {
            log.info("表单 {} 无启用绑定，跳过流程发起", cmd.getFormKey());
            return;
        }
        WorkflowFormBinding binding = bindings.get(0);

        // 2. 解析审批人
        ApproverContext ctx = new ApproverContext();
        ctx.setFormKey(cmd.getFormKey());
        ctx.setSubmittedData(cmd.getSubmittedData());
        ctx.setSubmitter(cmd.getSubmitter());
        ctx.setTenantId(cmd.getTenantId());
        String approver = approverResolver.resolve(ctx);

        log.debug("审批人解析完成: resolver={}, approver={}",
                approverResolver.getClass().getSimpleName(), approver);

        // 3. Flowable 发起（只放 id 引用变量，submittedData 不塞入流程变量）
        Map<String, Object> variables = new HashMap<>();
        variables.put("approver", approver);
        variables.put("formKey", cmd.getFormKey());
        variables.put("recordId", cmd.getRecordId());
        variables.put("submitter", String.valueOf(cmd.getSubmitter()));

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKeyAndTenantId(
                binding.getProcessDefKey(),
                cmd.getRecordId(),
                variables,
                String.valueOf(cmd.getTenantId())
        );

        log.info("Flowable 流程已发起: processInstanceId={}, processDefKey={}, "
                        + "businessKey={}, tenantId={}",
                processInstance.getId(), binding.getProcessDefKey(),
                cmd.getRecordId(), cmd.getTenantId());

        // 4. 落实例记录（基列由 MyBatis-Plus 拦截器自动注入，不手动填）
        WorkflowInstance instance = new WorkflowInstance();
        instance.setProcessInstanceId(processInstance.getId());
        instance.setProcessDefKey(binding.getProcessDefKey());
        instance.setBusinessKey(cmd.getRecordId());
        instance.setFormKey(cmd.getFormKey());
        instance.setInitiatorId(cmd.getSubmitter());
        instance.setStatus(InstanceStatusEnum.RUNNING.getCode());
        workflowInstanceService.save(instance);

        log.info("流程实例记录已保存: id={}, status=RUNNING", instance.getId());

        // 5. 发布 TODO_CREATED 通知事件（查询刚创建的 task）
        publishTodoCreatedEvent(processInstance.getId(), cmd);
    }

    /**
     * 查询流程实例的首个待办 task，发布 TODO_CREATED 通知事件。
     * <p>
     * 骨架阶段为单节点审批，预期只有一个 task；若查不到 task（非预期）
     * 仅 warn 日志不阻断主流程。
     * </p>
     */
    private void publishTodoCreatedEvent(String processInstanceId, StartCommand cmd) {
        List<Task> tasks = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .list();
        if (tasks.isEmpty()) {
            log.warn("流程 {} 无待办 task，跳过 TODO_CREATED 通知", processInstanceId);
            return;
        }
        Task task = tasks.get(0);
        Long approverId;
        try {
            approverId = Long.valueOf(task.getAssignee());
        } catch (NumberFormatException e) {
            log.warn("task assignee 非数字格式: assignee={}，跳过 TODO_CREATED 通知", task.getAssignee());
            return;
        }

        WorkflowNotifyEvent event = new WorkflowNotifyEvent(
                WorkflowNotifyTrigger.TODO_CREATED,
                approverId,
                cmd.getTenantId(),
                cmd.getSubmitter(),
                task.getId()
        );
        domainEventPublisher.publish(event);
        log.debug("TODO_CREATED 事件已发布: taskId={}, approverId={}", task.getId(), approverId);
    }
}
