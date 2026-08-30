package com.sw.ck.bpm.process.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待办任务响应 DTO。
 * <p>
 * 返回给前端的待办列表项，包含任务标识、流程实例标识、表单信息、业务键等。
 * formKey 和 businessKey 由 process 层通过绑定表/流程变量富化后组装。
 * </p>
 */
@Data
public class TodoTaskRespDTO {

    /** Flowable task ID */
    private String taskId;

    /** Flowable 流程实例 ID */
    private String processInstanceId;

    /** 流程名称 */
    private String processName;

    /** 表单业务标识 */
    private String formKey;

    /** 业务键（= 表单 recordId，反查表单提交数据用） */
    private String businessKey;

    /** 任务创建时间 */
    private LocalDateTime createTime;
}
