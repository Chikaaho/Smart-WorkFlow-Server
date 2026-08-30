package com.sw.ck.iot.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sw.ck.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * IoT 设备控制命令实体。
 * <p>
 * 支持两类控制语义：延迟生效（DEFERRED）和在线确认（ONLINE_CONFIRM）。
 * 命令状态：QUEUED → SENDING → SENT → DELIVERED → ACKED → SUCCESS / FAILED / UNKNOWN / EXPIRED。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sw_iot_device_command")
public class IotDeviceCommand extends BaseEntity {

    /**
     * 腾讯云产品 ID（与 device_name 组合定位设备）。
     */
    @TableField("product_id")
    private String productId;

    /**
     * 腾讯云设备名称。
     */
    @TableField("device_name")
    private String deviceName;

    /**
     * 本地业务标识（保留兼容）。
     */
    @TableField("device_key")
    private String deviceKey;

    /**
     * 命令类型：PROPERTY（属性下发）/ ACTION（行为调用）。
     */
    @TableField("command_type")
    private String commandType;

    /**
     * 命令标识（如 power_on / set_brightness）。
     */
    @TableField("command_key")
    private String commandKey;

    /**
     * 控制语义：DEFERRED（延迟生效）/ ONLINE_CONFIRM（在线确认）。
     */
    @TableField("semantic_mode")
    private String semanticMode;

    /**
     * 命令负载（属性 JSON 或行为输入 JSON）。
     */
    @TableField("payload")
    private String payload;

    /**
     * 命令状态：QUEUED / SENDING / SENT / DELIVERED / ACKED / SUCCESS / FAILED / UNKNOWN / EXPIRED。
     */
    @TableField("status")
    private String status;

    /**
     * 幂等键（防止重复发送）。
     */
    @TableField("idempotent_key")
    private String idempotentKey;

    /**
     * 命令过期时间。
     */
    @TableField("expiry_time")
    private LocalDateTime expiryTime;

    /**
     * 已尝试次数。
     */
    @TableField("retry_count")
    private Integer retryCount;

    /**
     * 最后失败原因。
     */
    @TableField("last_error")
    private String lastError;

    /**
     * 腾讯云 RequestId（属性下发时返回）。
     */
    @TableField("tencent_request_id")
    private String tencentRequestId;

    /**
     * 腾讯云 ClientToken（异步行为调用时返回）。
     */
    @TableField("client_token")
    private String clientToken;

    /**
     * 设备输出参数（同步行为调用时返回）。
     */
    @TableField("device_output")
    private String deviceOutput;

    /**
     * 设备执行结果（JSON 字符串）。
     */
    @TableField("result")
    private String result;

    /**
     * 关联审批业务 ID（流程实例 ID，审批结果驱动设备时写入）。
     */
    @TableField("approval_biz_id")
    private String approvalBizId;
}
