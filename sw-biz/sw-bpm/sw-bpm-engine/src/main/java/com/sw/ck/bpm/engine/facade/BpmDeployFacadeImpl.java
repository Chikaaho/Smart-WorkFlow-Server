package com.sw.ck.bpm.engine.facade;

import com.sw.ck.bpm.api.facade.BpmDeployFacade;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * BPM 部署门面实现 —— 封装 Flowable {@link RepositoryService}。
 */
@Service
public class BpmDeployFacadeImpl implements BpmDeployFacade {

    private static final Logger log = LoggerFactory.getLogger(BpmDeployFacadeImpl.class);

    private final RepositoryService repositoryService;

    public BpmDeployFacadeImpl(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Override
    public String deployClasspathBpmn(String resourcePath, String deploymentName) {
        Deployment deployment = repositoryService.createDeployment()
                .addClasspathResource(resourcePath)
                .name(deploymentName)
                .deploy();
        log.info("BPMN deployed: deploymentId={}, name={}, resource={}",
                deployment.getId(), deploymentName, resourcePath);
        return deployment.getId();
    }
}
