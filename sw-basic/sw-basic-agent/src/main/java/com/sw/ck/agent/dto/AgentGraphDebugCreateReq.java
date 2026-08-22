package com.sw.ck.agent.dto;

import lombok.Data;

/**
 * 创建调试会话请求（M07-F02-04）。
 */
@Data
public class AgentGraphDebugCreateReq {

    /** 图定义 id（必填） */
    private Long graphDefId;

    /** 调试入参文本（必填） */
    private String input;
}
