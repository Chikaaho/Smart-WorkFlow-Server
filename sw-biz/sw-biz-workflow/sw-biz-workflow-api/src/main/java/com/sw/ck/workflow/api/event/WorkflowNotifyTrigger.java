package com.sw.ck.workflow.api.event;

/**
 * 流程通知触发类型枚举。
 * <p>
 * 标识 {@link WorkflowNotifyEvent} 的触发原因，由 listener 根据此枚举
 * 映射到 {@code NotifyBizType} 和通知文案。
 * </p>
 */
public enum WorkflowNotifyTrigger {

    /**
     * 新待办创建：流程发起后，首个审批节点的 task 创建时触发。
     * 收件人为该 task 的审批人（approver）。
     */
    TODO_CREATED,

    /**
     * 流程审批通过：流程实例结束时触发（所有节点完成）。
     * 收件人为流程发起人（initiator）。
     */
    PROCESS_APPROVED
}
