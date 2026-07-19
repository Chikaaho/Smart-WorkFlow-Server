package com.sw.ck.bpm.process.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批历史条目 DTO。
 * <p>
 * 代表一个已完成的审批节点，记录谁在什么时候做了审批。
 * </p>
 */
@Data
public class ApprovalHistoryItemDTO {

    /** Flowable task ID */
    private String taskId;

    /** 任务名称（如"审批"） */
    private String taskName;

    /** 处理人用户 ID */
    private String assignee;

    /** 任务创建时间 */
    private LocalDateTime createTime;

    /** 任务完成时间 */
    private LocalDateTime endTime;
}
