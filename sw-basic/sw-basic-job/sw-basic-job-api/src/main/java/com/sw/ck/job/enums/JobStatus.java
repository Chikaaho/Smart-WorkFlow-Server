package com.sw.ck.job.enums;

/**
 * 定时任务调度状态枚举。
 * <p>
 * 定义于 {@code -api}，供调用方通过 Facade 传递状态参数。
 * </p>
 */
public enum JobStatus {

    /** 正常调度中 */
    NORMAL,

    /** 已暂停 */
    PAUSED,
}
