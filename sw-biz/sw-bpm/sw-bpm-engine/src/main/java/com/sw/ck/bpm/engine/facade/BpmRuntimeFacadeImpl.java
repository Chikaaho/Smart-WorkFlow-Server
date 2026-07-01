package com.sw.ck.bpm.engine.facade;

import com.sw.ck.bpm.api.facade.BpmRuntimeFacade;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * BPM 运行时门面实现 —— 封装 Flowable {@link RuntimeService}。
 */
@Service
public class BpmRuntimeFacadeImpl implements BpmRuntimeFacade {

    private static final Logger log = LoggerFactory.getLogger(BpmRuntimeFacadeImpl.class);

    private final RuntimeService runtimeService;

    public BpmRuntimeFacadeImpl(RuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @Override
    public String startProcess(String processDefKey, String businessKey,
                               Map<String, Object> variables, String tenantId) {
        ProcessInstance instance = runtimeService.startProcessInstanceByKeyAndTenantId(
                processDefKey, businessKey, variables, tenantId);
        log.info("BPM process started: processInstanceId={}, processDefKey={}, businessKey={}, tenantId={}",
                instance.getId(), processDefKey, businessKey, tenantId);
        return instance.getId();
    }
}
