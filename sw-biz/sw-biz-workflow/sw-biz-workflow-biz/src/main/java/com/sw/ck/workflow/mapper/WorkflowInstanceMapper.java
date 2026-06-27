package com.sw.ck.workflow.mapper;

import com.sw.ck.common.mapper.BaseMapperX;
import com.sw.ck.workflow.entity.WorkflowInstance;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流程实例记录 Mapper。
 */
@Mapper
public interface WorkflowInstanceMapper extends BaseMapperX<WorkflowInstance> {
}
