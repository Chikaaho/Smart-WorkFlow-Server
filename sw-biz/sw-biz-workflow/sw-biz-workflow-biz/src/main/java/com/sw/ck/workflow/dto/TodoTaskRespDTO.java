package com.sw.ck.workflow.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待办任务响应 DTO。
 * <p>
 * 返回给前端的待办列表项，包含任务标识、流程实例标识、表单信息、业务键等。
 * formKey 和 businessKey 从 Flowable 流程变量/ProcessInstance 获取，
 * 不依赖我方 WorkflowInstance 表（但后者通常也存在）。
 * </p>
 */
@Data
public class TodoTaskRespDTO {

    /** Flowable task ID */
    private String taskId;

    /** Flowable 流程实例 ID */
    private String processInstanceId;

    /** 表单业务标识 */
    private String formKey;

    /** 业务键（= 表单 recordId，反查表单提交数据用） */
    private String businessKey;

    /** 任务创建时间 */
    private LocalDateTime createTime;
}
