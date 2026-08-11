package com.sw.ck.agent.service;

import com.sw.ck.agent.dto.AgentGraphExecuteRespDTO;

/**
 * Agent 图执行 Service（M07-F02 Step8 图解释执行引擎第一版）。
 * <p>
 * 消费 Step7 的图定义（{@code sw_agent_graph_def} 的 {@code graph_json}）：校验图处于
 * 发布态 + 执行前最小校验（方案 §2-D）→ 调 {@code AgentGraphInterpreter} 解释执行。
 * 执行失败以 {@code success=false} 返回（不上抛），与 F01 run() 语义一致。
 * </p>
 */
public interface AgentGraphExecutionService {

    /**
     * 执行一次已发布图。
     *
     * @param graphDefId 图定义 id（不存在/跨租户 → NOT_FOUND，同 Step7 requireEntity）
     * @param input      执行入参文本（初始累积文本，空白 → PARAM_ERROR）
     * @return 执行结果（success/output/errorMessage/latencyMs）
     */
    AgentGraphExecuteRespDTO execute(Long graphDefId, String input);
}
