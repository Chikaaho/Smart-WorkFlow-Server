package com.sw.ck.agent.dto;

import lombok.Data;

/**
 * 图执行请求 DTO（M07-F02 Step8）。
 * <p>
 * 极简执行上下文：单一 {@code input} 文本作为初始累积文本（本版无多变量上下文，
 * 方案 §2-B 简化边界）。
 * </p>
 */
@Data
public class AgentGraphExecuteReqDTO {

    /** 执行入参文本（初始累积文本，必填，空白拒绝） */
    private String input;
}
