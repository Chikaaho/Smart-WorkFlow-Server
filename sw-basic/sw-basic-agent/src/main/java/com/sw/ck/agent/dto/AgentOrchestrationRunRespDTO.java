package com.sw.ck.agent.dto;

import lombok.Data;

/**
 * 编排执行响应 DTO（M07 Step2）。
 * <p>
 * 模型服务不可达 / 协议不支持 / 图执行异常均以 {@code success=false} + 非空
 * {@code errorMessage} 表达，不抛 500；{@code errorMessage} 只含异常摘要，
 * 绝不包含明文 API Key。
 * </p>
 */
@Data
public class AgentOrchestrationRunRespDTO {

    /** 是否执行成功 */
    private boolean success;

    /** 模型回复文本（成功时非空） */
    private String output;

    /** 失败原因摘要（不含明文 API Key） */
    private String errorMessage;

    /** 执行耗时（毫秒） */
    private long latencyMs;

    /**
     * 本次执行使用的会话 id（M07 Step4 F04）：请求未携带时返回新建会话 id，
     * 携带时原样返回；仅执行成功时设置（失败路径不暴露空会话）。
     */
    private Long sessionId;
}
