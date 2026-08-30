package com.sw.ck.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sw.ck.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent 图调试节点明细（M07-F02-04 图单步调试闭环）—— 对应
 * {@code sw_agent_graph_debug_node} 表（V36）。
 * <p>
 * 调试过程中每次节点出队产生一条访问记录：全局步序 {@code nodeSeq}（1-based，同一次
 * 调试内递增）+ 并行分支标识 {@code branchId} + 节点级耗时 + 该节点执行后的变量表
 * 快照（JSON）。与 {@link AgentGraphExecutionNode} 同构，但归属调试会话
 * {@code debugSessionId} 而非执行记录。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sw_agent_graph_debug_node")
public class AgentGraphDebugNode extends BaseEntity {

    /** 所属调试会话 id（sw_agent_graph_debug_session） */
    private Long debugSessionId;

    /** 本次调试内全局访问步序（1-based，出队即分配） */
    private Integer nodeSeq;

    /** 并行分支标识（"0" 根路径；FORK 后追加出边下标） */
    private String branchId;

    /** 图节点 id */
    private String nodeId;

    /** 节点类型（START/LLM/TOOL/CONDITION/LOOP/FORK/JOIN/END） */
    private String nodeType;

    /** 节点级耗时（毫秒，出队到本步路由完成） */
    private Long nodeLatencyMs;

    /** 该节点执行后的变量表快照（JSON） */
    private String variableSnapshot;

    /** 该节点 LLM 调用的输入 Token（非 LLM 节点或供应商未返回时为 NULL） */
    private Long inputTokens;

    /** 该节点 LLM 调用的输出 Token（非 LLM 节点或供应商未返回时为 NULL） */
    private Long outputTokens;
}
