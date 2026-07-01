package com.sw.ck.bpm.process.service;

import com.sw.ck.bpm.api.dto.ApproverContext;
import com.sw.ck.bpm.api.dto.BpmTaskDTO;
import com.sw.ck.bpm.api.event.BpmNotifyEvent;
import com.sw.ck.bpm.api.event.BpmNotifyTrigger;
import com.sw.ck.bpm.api.facade.BpmRuntimeFacade;
import com.sw.ck.bpm.api.facade.BpmTaskFacade;
import com.sw.ck.bpm.api.spi.ApproverResolver;
import com.sw.ck.bpm.process.dto.StartCommand;
import com.sw.ck.bpm.process.entity.BpmFormBinding;
import com.sw.ck.bpm.process.entity.BpmInstance;
import com.sw.ck.bpm.process.entity.InstanceStatusEnum;
import com.sw.ck.common.event.DomainEventPublisher;
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
 * 封装表单绑定查询 → 审批人解析 → 流程发起 → 实例落库 四个步骤，
 * 由 {@code @Transactional} 保证流程发起 + 实例插入的原子性。
 * </p>
 *
 * <p>
 * 调用方：
 * <ul>
 *   <li>{@link com.sw.ck.bpm.process.listener.FormSubmittedEventListener}
 *       — 表单提交事件 {@code AFTER_COMMIT} 后异步触发</li>
 *   <li>后续 {@code ScheduledFlowTriggerEvent} 监听器 — 定时任务 FLOW 类型发起</li>
 * </ul>
 * </p>
 *
 * <p>
 * 防腐：所有引擎操作经 {@link BpmRuntimeFacade} / {@link BpmTaskFacade}，
 * 不 import 任何 Flowable 类型。
 * </p>
 *
 * <p>
 * 事务边界：{@code @Transactional} 仅包围 bpm 侧的操作（流程发起 + 实例落库），
 * 表单提交的事务已在 {@code AFTER_COMMIT} 时独立提交。流程发起失败不回滚表单数据，
 * 这是预期的解耦设计。
 * </p>
 */
@Service
public class ProcessStartService {

    private static final Logger log = LoggerFactory.getLogger(ProcessStartService.class);

    private final BpmFormBindingService bindingService;
    private final ApproverResolver approverResolver;
    private final BpmRuntimeFacade bpmRuntimeFacade;
    private final BpmTaskFacade bpmTaskFacade;
    private final BpmInstanceService bpmInstanceService;
    private final DomainEventPublisher domainEventPublisher;

    public ProcessStartService(BpmFormBindingService bindingService,
                                ApproverResolver approverResolver,
                                BpmRuntimeFacade bpmRuntimeFacade,
                                BpmTaskFacade bpmTaskFacade,
                                BpmInstanceService bpmInstanceService,
                                DomainEventPublisher domainEventPublisher) {
        this.bindingService = bindingService;
        this.approverResolver = approverResolver;
        this.bpmRuntimeFacade = bpmRuntimeFacade;
        this.bpmTaskFacade = bpmTaskFacade;
        this.bpmInstanceService = bpmInstanceService;
        this.domainEventPublisher = domainEventPublisher;
    }

    /**
     * 发起流程实例。
     * <ol>
     *   <li>查启用绑定 — 无绑定则 info 日志 return（no-op，不是每个表单都走流程）</li>
     *   <li>解析审批人 — 经 {@link ApproverResolver} 获取 approver 变量值</li>
     *   <li>经 Facade 发起 — {@code bpmRuntimeFacade.startProcess(...)}</li>
     *   <li>落实例 — {@code sw_bpm_instance} 写入 RUNNING 状态（基列靠拦截器自动注入）</li>
     * </ol>
     *
     * @param cmd 发起命令，不可为空
     */
    @Transactional
    public void start(StartCommand cmd) {
        // 1. 查启用绑定
        List<BpmFormBinding> bindings = bindingService.findActiveByFormKey(cmd.getFormKey());
        if (bindings.isEmpty()) {
            log.info("表单 {} 无启用绑定，跳过流程发起", cmd.getFormKey());
            return;
        }
        BpmFormBinding binding = bindings.get(0);

        // 2. 解析审批人
        ApproverContext ctx = new ApproverContext();
        ctx.setFormKey(cmd.getFormKey());
        ctx.setSubmittedData(cmd.getSubmittedData());
        ctx.setSubmitter(cmd.getSubmitter());
        ctx.setTenantId(cmd.getTenantId());
        String approver = approverResolver.resolve(ctx);

        log.debug("审批人解析完成: resolver={}, approver={}",
                approverResolver.getClass().getSimpleName(), approver);

        // 3. 经 Facade 发起（只放 id 引用变量，submittedData 不塞入流程变量）
        Map<String, Object> variables = new HashMap<>();
        variables.put("approver", approver);
        variables.put("formKey", cmd.getFormKey());
        variables.put("recordId", cmd.getRecordId());
        variables.put("submitter", String.valueOf(cmd.getSubmitter()));

        // 原: runtimeService.startProcessInstanceByKeyAndTenantId(...)
        // → bpmRuntimeFacade.startProcess(...)
        String processInstanceId = bpmRuntimeFacade.startProcess(
                binding.getProcessDefKey(),
                cmd.getRecordId(),
                variables,
                String.valueOf(cmd.getTenantId())
        );

        log.info("流程已发起: processInstanceId={}, processDefKey={}, businessKey={}, tenantId={}",
                processInstanceId, binding.getProcessDefKey(), cmd.getRecordId(), cmd.getTenantId());

        // 4. 落实例记录（基列由 MyBatis-Plus 拦截器自动注入，不手动填）
        BpmInstance instance = new BpmInstance();
        instance.setProcessInstanceId(processInstanceId);
        instance.setProcessDefKey(binding.getProcessDefKey());
        instance.setBusinessKey(cmd.getRecordId());
        instance.setFormKey(cmd.getFormKey());
        instance.setInitiatorId(cmd.getSubmitter());
        instance.setStatus(InstanceStatusEnum.RUNNING.getCode());
        bpmInstanceService.save(instance);

        log.info("流程实例记录已保存: id={}, status=RUNNING", instance.getId());

        // 5. 发布 TODO_CREATED 通知事件（查询刚创建的 task）
        publishTodoCreatedEvent(processInstanceId, cmd);
    }

    /**
     * 查询流程实例的首个待办 task，发布 TODO_CREATED 通知事件。
     * <p>
     * 骨架阶段为单节点审批，经 Facade 查询审批人待办并匹配 processInstanceId。
     * 若查不到 task（非预期）仅 warn 日志不阻断主流程。
     * </p>
     */
    private void publishTodoCreatedEvent(String processInstanceId, StartCommand cmd) {
        // 经 Facade 按流程实例精确查询刚创建的任务
        List<BpmTaskDTO> tasks = bpmTaskFacade.queryByProcessInstance(processInstanceId);
        BpmTaskDTO matchedTask = tasks.stream()
                .findFirst()
                .orElse(null);

        if (matchedTask == null) {
            log.warn("流程 {} 无待办 task，跳过 TODO_CREATED 通知", processInstanceId);
            return;
        }

        Long approverId;
        try {
            approverId = Long.valueOf(matchedTask.getAssignee());
        } catch (NumberFormatException e) {
            log.warn("task assignee 非数字格式: assignee={}，跳过 TODO_CREATED 通知",
                    matchedTask.getAssignee());
            return;
        }

        BpmNotifyEvent event = new BpmNotifyEvent(
                BpmNotifyTrigger.TODO_CREATED,
                approverId,
                cmd.getTenantId(),
                cmd.getSubmitter(),
                matchedTask.getTaskId()
        );
        domainEventPublisher.publish(event);
        log.debug("TODO_CREATED 事件已发布: taskId={}, approverId={}",
                matchedTask.getTaskId(), approverId);
    }
}
