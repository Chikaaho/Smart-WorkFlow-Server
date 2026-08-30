package com.sw.ck.notify.api;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 发送通知命令值对象（不可变）。
 * <p>
 * 由 {@link NotifyFacade#send(SendNotifyCommand)} 消费，
 * 通知实现的唯一入参。
 * </p>
 */
@Data
@AllArgsConstructor
public class SendNotifyCommand {

    /** 接收人用户 ID */
    private final Long recipientId;

    /** 通知标题 */
    private final String title;

    /** 通知内容 */
    private final String content;

    /** 业务类型 */
    private final NotifyBizType bizType;

    /** 业务 ID（Flowable taskId / piId / 业务单号等） */
    private final String bizId;

    /** 租户 ID，供 Step 2 listener 还原上下文；本步实现不使用此字段落库 */
    private final Long tenantId;
}
