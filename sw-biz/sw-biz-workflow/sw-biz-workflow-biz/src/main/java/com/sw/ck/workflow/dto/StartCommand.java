package com.sw.ck.workflow.dto;

import lombok.Data;

import java.util.Map;

/**
 * 流程发起命令值对象。
 * <p>
 * {@link com.sw.ck.workflow.service.impl.ProcessStartService#start(StartCommand)}
 * 的唯一入参，由事件监听器（{@link com.sw.ck.workflow.listener.FormSubmittedEventListener}）
 * 或定时任务监听器（后续 {@code ScheduledFlowTriggerEvent}）组装后传入。
 * </p>
 */
@Data
public class StartCommand {

    /** 表单业务标识 */
    private String formKey;

    /** 提交记录 UUID（= 动态宽表主键，作 Flowable businessKey） */
    private String recordId;

    /** 发起人用户 ID */
    private Long submitter;

    /** 租户 ID */
    private Long tenantId;

    /** 表单提交数据（字段名 → 值） */
    private Map<String, Object> submittedData;
}
