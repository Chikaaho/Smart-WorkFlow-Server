package com.sw.ck.job.enums;

/**
 * 定时任务类型枚举。
 * <p>
 * 定义于 {@code -api}，供其他模块（如 BPM）引用以区分任务类型。
 * </p>
 */
public enum JobType {

    /** Spring Bean 处理器任务 */
    BEAN,

    /** 定时发起流程任务 */
    FLOW,
}
