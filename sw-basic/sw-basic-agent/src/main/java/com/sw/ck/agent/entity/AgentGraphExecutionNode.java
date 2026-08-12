package com.sw.ck.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sw.ck.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent 图执行节点明细（M07 Step12 图执行历史持久化）—— 对应
 * {@code sw_agent_graph_execution_node} 表（V28）。
 * <p>
 * 解释器每次节点出队产生一条访问记录：全局步序 {@code nodeSeq}（1-based，同一次
 * 执行内递增）+ 并行分支标识 {@code branchId}（FORK 扇出按出边顺序追加下标，如
 * "0"/"0-1"/"0-2"；非 FORK 路径恒为 "0"；LOOP 同分支迭代 = 多条 node_seq 递增记录）
 * + 节点级耗时 + 该节点执行后的变量表快照（JSON 字符串，H2=CLOB / PG=TEXT）。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sw_agent_graph_execution_node")
public class AgentGraphExecutionNode extends BaseEntity {

    /** 所属执行记录 id（sw_agent_graph_execution） */
    private Long executionId;

    /** 本次执行内全局访问步序（1-based，出队即分配） */
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
}
