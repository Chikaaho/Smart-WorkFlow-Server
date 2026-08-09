package com.sw.ck.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sw.ck.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工具调用日志（M07 Step4 F04 对话交互）。
 * <p>
 * 每轮编排中实际发生的工具调用（FunctionToolCallback lambda 包装计时）逐条落库：
 * 工具名 + 入参 JSON + 返回 JSON + 耗时；入参与返回可能较长（H2=CLOB / PG=TEXT）。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sw_agent_tool_call_log")
public class AgentToolCallLog extends BaseEntity {

    /** 所属会话 id（sw_agent_session） */
    private Long sessionId;

    /** 工具名（对应白名单表 name） */
    private String toolName;

    /** 工具入参（JSON 字符串，可能较长） */
    private String toolCallArgs;

    /** 工具返回（JSON 字符串，可能较长） */
    private String toolCallResult;

    /** 工具执行耗时（毫秒） */
    private Long latencyMs;
}
