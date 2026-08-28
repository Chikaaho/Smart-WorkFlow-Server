package com.sw.ck.iot.enums;

/**
 * IoT 设备命令状态枚举。
 * <p>
 * 命令状态流转：
 * QUEUED → SENDING → SENT → DELIVERED → ACKED → SUCCESS / FAILED / UNKNOWN / EXPIRED。
 * </p>
 */
public enum CommandStatus {

    /**
     * 已入队：命令已持久化进入本地待发送队列。
     */
    QUEUED("已入队"),

    /**
     * 发送中：正在调用腾讯云 API。
     */
    SENDING("发送中"),

    /**
     * 腾讯已发送：腾讯云已接受请求并返回 RequestId。
     */
    SENT("腾讯已发送"),

    /**
     * 设备已送达：确认控制信号已送达设备（属性控制时使用）。
     */
    DELIVERED("设备已送达"),

    /**
     * 设备已回复：设备已回复行为调用（行为控制时使用）。
     */
    ACKED("设备已回复"),

    /**
     * 成功：设备业务执行成功。
     */
    SUCCESS("成功"),

    /**
     * 失败：命令执行失败（可重试的瞬时失败）。
     */
    FAILED("失败"),

    /**
     * 结果未知：腾讯 API 调用成功但无法确认设备是否执行（不可自动重试）。
     */
    UNKNOWN("结果未知"),

    /**
     * 已过期：命令超过有效期未处理。
     */
    EXPIRED("已过期");

    private final String description;

    CommandStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 判断状态是否为终态（SUCCESS / FAILED / UNKNOWN / EXPIRED）。
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == UNKNOWN || this == EXPIRED;
    }

    /**
     * 判断是否可重试（QUEUED / FAILED）。
     */
    public boolean isRetryable() {
        return this == QUEUED || this == FAILED;
    }
}
