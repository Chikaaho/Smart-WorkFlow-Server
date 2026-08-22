package com.sw.ck.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sw.ck.agent.entity.AgentGraphDebugNode;

/**
 * Agent 图调试节点明细 Mapper（M07-F02-04 图单步调试闭环）。
 * <p>
 * 全部数据访问走 BaseMapper + 租户拦截器（同 V28 表同款惯例）；
 * 明细按 {@code debug_session_id + node_seq} 升序经 Wrappers 查询。
 * </p>
 */
public interface AgentGraphDebugNodeMapper extends BaseMapper<AgentGraphDebugNode> {
}
