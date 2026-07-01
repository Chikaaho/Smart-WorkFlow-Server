package com.sw.ck.bpm.process.service.impl;

import com.sw.ck.bpm.process.entity.BpmInstance;
import com.sw.ck.bpm.process.mapper.BpmInstanceMapper;
import com.sw.ck.bpm.process.service.BpmInstanceService;
import com.sw.ck.common.service.BaseServiceImpl;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 流程实例记录 Service 实现。
 */
@Service
public class BpmInstanceServiceImpl
        extends BaseServiceImpl<BpmInstanceMapper, BpmInstance>
        implements BpmInstanceService {

    @Override
    public Optional<BpmInstance> findByProcessInstanceId(String processInstanceId) {
        return Optional.ofNullable(
                lambdaQuery()
                        .eq(BpmInstance::getProcessInstanceId, processInstanceId)
                        .one()
        );
    }

    @Override
    public Optional<BpmInstance> findByBusinessKey(String businessKey) {
        return Optional.ofNullable(
                lambdaQuery()
                        .eq(BpmInstance::getBusinessKey, businessKey)
                        .one()
        );
    }

    @Override
    public void updateStatus(String processInstanceId, String status) {
        lambdaUpdate()
                .eq(BpmInstance::getProcessInstanceId, processInstanceId)
                .set(BpmInstance::getStatus, status)
                .update();
    }
}
