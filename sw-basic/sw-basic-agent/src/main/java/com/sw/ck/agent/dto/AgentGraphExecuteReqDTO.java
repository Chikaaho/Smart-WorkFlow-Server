package com.sw.ck.agent.dto;

import lombok.Data;

/**
 * 图执行请求 DTO（M07-F02 Step8 + Step10 多变量执行上下文）。
 * <p>
 * 单一 {@code input} 文本写入执行上下文默认变量（Step10：节点未指定变量名时读写
 * 默认变量，旧图零迁移；节点可经 config.inputVar/outputVar 存取命名变量）。
 * </p>
 */
@Data
public class AgentGraphExecuteReqDTO {

    /** 执行入参文本（写入默认变量，必填，空白拒绝） */
    private String input;
}
