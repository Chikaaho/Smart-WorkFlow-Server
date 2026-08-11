package com.sw.ck.agent.dto;

import lombok.Data;

/**
 * 图执行响应 DTO（M07-F02 Step8）。
 * <p>
 * 语义对齐 F01 {@code AgentOrchestrationRunRespDTO}：图执行运行时错误（条件无匹配且
 * 无默认边 / 步数超限 / 模型或工具调用异常）以 {@code success=false} + 非空
 * {@code errorMessage} 表达，不上抛（与 F01 run() success=false 语义一致）；
 * {@code errorMessage} 只含异常摘要，绝不包含明文 API Key。
 * </p>
 */
@Data
public class AgentGraphExecuteRespDTO {

    /** 是否执行成功 */
    private boolean success;

    /** 最终输出文本（END 节点处的累积文本，成功时非空） */
    private String output;

    /** 失败原因摘要（不含明文 API Key） */
    private String errorMessage;

    /** 执行耗时（毫秒） */
    private long latencyMs;
}
