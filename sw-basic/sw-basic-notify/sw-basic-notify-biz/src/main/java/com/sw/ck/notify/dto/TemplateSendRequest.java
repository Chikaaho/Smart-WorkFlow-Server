package com.sw.ck.notify.dto;

import lombok.Data;

import java.util.Map;

/**
 * 按模板发送请求：模板代码 + 接收人 + 变量值。
 * <p>
 * 模板不可用（停用/删除/不存在）或变量缺失时在落库前拒绝（方向 §3.3）。
 * bizType 固定 {@code NotifyBizType.SYSTEM}，bizId 可空。
 * </p>
 */
@Data
public class TemplateSendRequest {

    /** 稳定模板代码 */
    private String templateCode;

    /** 接收人用户 ID */
    private Long recipientId;

    /** 变量值（key=变量名，value=纯文本） */
    private Map<String, String> variables;
}
