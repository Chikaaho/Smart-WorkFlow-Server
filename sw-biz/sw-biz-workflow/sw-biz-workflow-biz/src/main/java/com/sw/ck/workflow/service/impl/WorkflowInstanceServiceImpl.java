package com.sw.ck.workflow.service.impl;

import com.sw.ck.common.service.BaseServiceImpl;
import com.sw.ck.workflow.entity.WorkflowInstance;
import com.sw.ck.workflow.mapper.WorkflowInstanceMapper;
import com.sw.ck.workflow.service.WorkflowInstanceService;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 流程实例记录 Service 实现。
 */
@Service
public class WorkflowInstanceServiceImpl
        extends BaseServiceImpl<WorkflowInstanceMapper, WorkflowInstance>
        implements WorkflowInstanceService {

    @Override
    public Optional<WorkflowInstance> findByProcessInstanceId(String processInstanceId) {
        return Optional.ofNullable(
                lambdaQuery()
                        .eq(WorkflowInstance::getProcessInstanceId, processInstanceId)
                        .one()
        );
    }

    @Override
    public Optional<WorkflowInstance> findByBusinessKey(String businessKey) {
        return Optional.ofNullable(
                lambdaQuery()
                        .eq(WorkflowInstance::getBusinessKey, businessKey)
                        .one()
        );
    }

    @Override
    public void updateStatus(String processInstanceId, String status) {
        lambdaUpdate()
                .eq(WorkflowInstance::getProcessInstanceId, processInstanceId)
                .set(WorkflowInstance::getStatus, status)
                .update();
    }
}
