package com.sw.ck.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sw.ck.agent.entity.AgentGraphDebugSession;

/**
 * Agent 图调试会话 Mapper（M07-F02-04 图单步调试闭环）。
 * <p>
 * 全部数据访问走 BaseMapper + 租户拦截器（同 V27/V28 表同款惯例）。
 * </p>
 */
public interface AgentGraphDebugSessionMapper extends BaseMapper<AgentGraphDebugSession> {
}
