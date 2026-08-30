package com.sw.ck.notify.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量发送站内通知响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotifyBatchSendResp {

    /** 最终去重后的接收人数 */
    private int recipientCount;
}
