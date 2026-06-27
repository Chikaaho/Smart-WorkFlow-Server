package com.sw.ck.workflow.service.impl;

import com.sw.ck.common.service.BaseServiceImpl;
import com.sw.ck.workflow.entity.WorkflowFormBinding;
import com.sw.ck.workflow.mapper.WorkflowFormBindingMapper;
import com.sw.ck.workflow.service.WorkflowFormBindingService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 表单↔流程绑定 Service 实现。
 */
@Service
public class WorkflowFormBindingServiceImpl
        extends BaseServiceImpl<WorkflowFormBindingMapper, WorkflowFormBinding>
        implements WorkflowFormBindingService {

    @Override
    public List<WorkflowFormBinding> findActiveByFormKey(String formKey) {
        return lambdaQuery()
                .eq(WorkflowFormBinding::getFormKey, formKey)
                .eq(WorkflowFormBinding::getActive, Boolean.TRUE)
                .list();
    }
}
