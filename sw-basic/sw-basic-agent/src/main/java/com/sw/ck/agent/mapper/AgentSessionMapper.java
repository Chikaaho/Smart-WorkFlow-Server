package com.sw.ck.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sw.ck.agent.entity.AgentSession;

/**
 * Agent 会话主表 Mapper。
 * <p>
 * 会话查询（按会话/按配置+用户）经 MyBatis-Plus Wrappers 在 Service 层链式构造——
 * agent 模块无 {@code @Select} 注解先例、仓库零 XML mapper（现场验证 V3），
 * 全部数据访问走 BaseMapper + 租户拦截器（同 V19/V20 表同款惯例）。
 * </p>
 */
public interface AgentSessionMapper extends BaseMapper<AgentSession> {
}
