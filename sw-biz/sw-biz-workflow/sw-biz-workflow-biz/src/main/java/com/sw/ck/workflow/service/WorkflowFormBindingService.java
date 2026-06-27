package com.sw.ck.workflow.service;

import com.sw.ck.common.service.BaseService;
import com.sw.ck.workflow.entity.WorkflowFormBinding;

import java.util.List;

/**
 * 表单↔流程绑定 Service。
 */
public interface WorkflowFormBindingService extends BaseService<WorkflowFormBinding> {

    /**
     * 查询当前租户下指定表单的启用绑定。
     *
     * @param formKey 表单业务标识
     * @return 启用绑定列表（理论上最多一条，但遵循骨架原则不做强制）
     */
    List<WorkflowFormBinding> findActiveByFormKey(String formKey);
}
