package com.sw.ck.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sw.ck.agent.entity.AgentToolCallLog;

/**
 * 工具调用日志 Mapper。
 * <p>
 * 查询经 MyBatis-Plus Wrappers（agent 模块惯例，见 {@code AgentSessionMapper} 说明），
 * 租户隔离由租户拦截器自动完成。
 * </p>
 */
public interface AgentToolCallLogMapper extends BaseMapper<AgentToolCallLog> {
}
