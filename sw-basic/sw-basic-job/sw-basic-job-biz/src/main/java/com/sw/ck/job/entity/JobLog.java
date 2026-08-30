package com.sw.ck.job.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sw.ck.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 定时任务执行日志实体。
 * <p>
 * 记录每次任务触发的执行详情。{@code tenant_id / 审计列 / deleted / version}
 * 由 MyBatis-Plus 拦截器自动注入。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sw_job_log")
public class JobLog extends BaseEntity {

    /** 关联任务 ID（sw_job_info.id） */
    private Long jobId;

    /** 任务名称（冗余字段，便于查询） */
    private String jobName;

    /** 任务组（冗余字段） */
    private String jobGroup;

    /** 触发方式（AUTO=定时触发 / MANUAL=手动触发） */
    private String triggerType;

    /** 任务参数（执行时传入的参数快照） */
    private String jobParams;

    /** 执行状态（RUNNING=执行中 / SUCCESS=成功 / FAILED=失败） */
    private String execStatus;

    /** 执行开始时间 */
    private LocalDateTime startTime;

    /** 执行结束时间 */
    private LocalDateTime endTime;

    /** 执行耗时（毫秒） */
    private Long duration;

    /** 执行结果/异常信息 */
    private String resultMsg;

    /** 异常堆栈（仅失败时记录） */
    private String exceptionStack;
}
