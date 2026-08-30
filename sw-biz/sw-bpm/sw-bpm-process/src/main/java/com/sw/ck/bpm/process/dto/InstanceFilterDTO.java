package com.sw.ck.bpm.process.dto;

import lombok.Data;

/**
 * 流程实例查询过滤参数。
 * <p>
 * 所有字段均为可选——null 或空字符串表示不施加该过滤条件。
 * </p>
 */
@Data
public class InstanceFilterDTO {

    /** 按实例状态过滤：RUNNING / APPROVED / REJECTED（null = 不过滤） */
    private String status;

    /** 按流程定义 key 过滤（null = 不过滤） */
    private String processDefKey;

    /** 按发起人用户 ID 过滤（null = 不过滤） */
    private Long initiatorId;

    /** 按业务键（表单记录 ID）过滤（null = 不过滤），用于表单记录与流程实例勾稽 */
    private String businessKey;

}
