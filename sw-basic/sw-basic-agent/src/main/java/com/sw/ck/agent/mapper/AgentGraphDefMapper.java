package com.sw.ck.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sw.ck.agent.entity.AgentGraphDef;

/**
 * Agent 图定义 Mapper —— 走 MyBatis-Plus 常规通道，@TableLogic + 租户拦截器自动生效。
 */
public interface AgentGraphDefMapper extends BaseMapper<AgentGraphDef> {
}
