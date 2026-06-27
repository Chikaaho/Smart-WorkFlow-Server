package com.sw.ck.workflow.mapper;

import com.sw.ck.common.mapper.BaseMapperX;
import com.sw.ck.workflow.entity.WorkflowFormBinding;
import org.apache.ibatis.annotations.Mapper;

/**
 * 表单↔流程绑定 Mapper。
 */
@Mapper
public interface WorkflowFormBindingMapper extends BaseMapperX<WorkflowFormBinding> {
}
