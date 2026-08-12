package com.sw.ck.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sw.ck.agent.entity.AgentGraphExecution;

/**
 * Agent 图执行记录 Mapper（M07 Step12）。
 * <p>
 * 全部数据访问走 BaseMapper + 租户拦截器（agent 模块无 {@code @Select} 注解先例、
 * 仓库零 XML mapper，同 V21-V25 表同款惯例）；查询经 Wrappers 在 Service 层链式构造。
 * </p>
 */
public interface AgentGraphExecutionMapper extends BaseMapper<AgentGraphExecution> {
}
