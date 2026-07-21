package com.sw.ck.job.event;

import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * FLOW 类型定时任务触发事件。
 * <p>
 * 当 {@code job_type=FLOW} 的定时任务到达执行时间时，调度器发布此事件。
 * BPM 模块或其他流程引擎监听此事件，复用与手动表单提交相同的校验+发起路径。
 * </p>
 *
 * <h3>幂等去重键</h3>
 * 使用 {@code jobId + fireTime} 作为幂等去重键。
 * BPM 侧监听方应在发起流程前基于此键去重。
 */
@Getter
@ToString
public class ScheduledFlowTriggerEvent implements Serializable {

    /** 定时任务 ID（sw_job_info.id） */
    private final Long jobId;

    /** 流程定义 Key */
    private final String flowDefKey;

    /** 表单数据（JSON 字符串） */
    private final String formData;

    /** 触发时间（用于幂等去重） */
    private final LocalDateTime fireTime;

    /** 租户 ID */
    private final Long tenantId;

    public ScheduledFlowTriggerEvent(Long jobId, String flowDefKey, String formData,
                                      LocalDateTime fireTime, Long tenantId) {
        this.jobId = jobId;
        this.flowDefKey = flowDefKey;
        this.formData = formData;
        this.fireTime = fireTime;
        this.tenantId = tenantId;
    }
}
