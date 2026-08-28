package com.sw.ck.bpm.api.event;

import lombok.Getter;

import java.io.Serializable;

/**
 * 审批通过后驱动设备的命令事件。
 * <p>
 * 当流程变量携带 productId + deviceName + commandKey 时，审批通过（实例 APPROVED）
 * 由 {@code BpmTodoController} 发布，由 process 侧 listener 异步消费，
 * 经 {@code IotDeviceFacade} 下发设备命令（审批结果驱动设备）。
 * </p>
 */
@Getter
public class BpmDeviceCommandEvent implements Serializable {

    /**
     * 流程实例 ID（作为 approvalBizId 写入命令记录）。
     */
    private final String processInstanceId;

    /**
     * 腾讯云产品 ID。
     */
    private final String productId;

    /**
     * 腾讯云设备名称。
     */
    private final String deviceName;

    /**
     * 命令标识（流程变量透传自表单提交数据）。
     */
    private final String commandKey;

    /**
     * 命令类型（PROPERTY / ACTION）。
     */
    private final String commandType;

    /**
     * 租户 ID（异步 listener 还原上下文）。
     */
    private final Long tenantId;

    /**
     * 操作人用户 ID（审批人，用于异步线程还原 LoginUserHolder）。
     */
    private final Long actorUserId;

    public BpmDeviceCommandEvent(String processInstanceId, String productId, String deviceName,
                                 String commandKey, String commandType,
                                 Long tenantId, Long actorUserId) {
        this.processInstanceId = processInstanceId;
        this.productId = productId;
        this.deviceName = deviceName;
        this.commandKey = commandKey;
        this.commandType = commandType;
        this.tenantId = tenantId;
        this.actorUserId = actorUserId;
    }
}
