package com.sw.ck.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sw.ck.agent.entity.AgentGraphExecutionNode;

/**
 * Agent 图执行节点明细 Mapper（M07 Step12）。
 * <p>
 * 全部数据访问走 BaseMapper + 租户拦截器（同 V28 表同款惯例）；
 * 明细按 {@code execution_id + node_seq} 升序经 Wrappers 查询。
 * </p>
 */
public interface AgentGraphExecutionNodeMapper extends BaseMapper<AgentGraphExecutionNode> {
}
