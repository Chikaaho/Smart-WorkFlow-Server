package com.sw.ck.agent.orchestration;

/**
 * 工具调用记录（M07 Step4 F04）：FunctionToolCallback lambda 包装捕获的单次调用摘要。
 * <p>
 * 轻量不可变 POJO（toolName/args/result/latencyMs），<b>不入库</b>——仅作
 * {@code TOOL_CALL_RECORDS_BINDING} ThreadLocal 传递载体，由编排 ServiceImpl 在
 * invoke 后读取并逐条落库 {@code sw_agent_tool_call_log}。
 * </p>
 */
public class ToolCallRecord {

    private final String toolName;
    private final String args;
    private final String result;
    private final long latencyMs;

    public ToolCallRecord(String toolName, String args, String result, long latencyMs) {
        this.toolName = toolName;
        this.args = args;
        this.result = result;
        this.latencyMs = latencyMs;
    }

    public String getToolName() {
        return toolName;
    }

    public String getArgs() {
        return args;
    }

    public String getResult() {
        return result;
    }

    public long getLatencyMs() {
        return latencyMs;
    }
}
