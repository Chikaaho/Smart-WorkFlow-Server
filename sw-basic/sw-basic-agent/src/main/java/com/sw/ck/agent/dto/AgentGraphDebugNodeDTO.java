package com.sw.ck.agent.dto;

import lombok.Data;

/**
 * 图调试节点明细 DTO（M07-F02-04 单步调试）。
 * <p>
 * 与 {@link AgentGraphExecutionNodeDTO} 同构，但归属调试会话。
 * </p>
 */
@Data
public class AgentGraphDebugNodeDTO {

    private Long id;

    private Long debugSessionId;

    private Integer nodeSeq;

    private String branchId;

    private String nodeId;

    private String nodeType;

    private Long nodeLatencyMs;

    private String variableSnapshot;

    private Long inputTokens;

    private Long outputTokens;
}
