package com.sw.ck.bpm.api.facade;

/**
 * BPM 部署门面 —— 封装流程引擎 RepositoryService。
 * <p>
 * 定义流程定义部署操作契约。
 * 实现类位于 sw-bpm-engine（闭源），由 Spring 注入。
 * </p>
 *
 * @since 1.0.0
 */
public interface BpmDeployFacade {

    /**
     * 从 classpath 部署 BPMN 文件。
     *
     * @param resourcePath    classpath 下的 BPMN 资源路径
     * @param deploymentName  部署名称
     * @return 部署 ID
     */
    String deployClasspathBpmn(String resourcePath, String deploymentName);
}
