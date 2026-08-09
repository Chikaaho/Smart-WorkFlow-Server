package com.sw.ck.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sw.ck.agent.entity.AgentMessage;

/**
 * 会话消息明细 Mapper。
 * <p>
 * {@code selectBySessionId} 语义（按会话加载消息、msg_order 升序）经 MyBatis-Plus
 * Wrappers 在 Service 层链式构造——agent 模块无 {@code @Select} 注解先例、仓库零
 * XML mapper（现场验证 V3），租户隔离由租户拦截器自动完成（同模块全部查询路径）。
 * </p>
 */
public interface AgentMessageMapper extends BaseMapper<AgentMessage> {
}
