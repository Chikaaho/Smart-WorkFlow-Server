package com.sw.ck.bpm.api.facade;

import com.sw.ck.bpm.api.dto.BpmDeployResult;
import com.sw.ck.bpm.api.dto.ProcessGraph;

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

    /**
     * 将 {@link ProcessGraph} 翻译为 BPMN XML 字节数组。
     * <p>
     * 使用 BpmnModel API + BpmnXMLConverter 生成标准 BPMN 2.0 XML，
     * 禁手拼 XML 字符串。
     * </p>
     *
     * @param graph 流程设计器图模型
     * @return BPMN 2.0 XML 字节数组
     */
    byte[] translateToBpmn(ProcessGraph graph);

    /**
     * 部署内存中的 BPMN XML。
     * <p>
     * 经 Flowable RepositoryService 部署，返回部署结果。
     * 不破坏 {@link #deployClasspathBpmn(String, String)}（Walking Skeleton 仍用）。
     * </p>
     *
     * @param bpmnXml        BPMN 2.0 XML 字节数组
     * @param deploymentName 部署名称
     * @return 部署结果（含 deploymentId + processDefinitionId）
     */
    BpmDeployResult deployModel(byte[] bpmnXml, String deploymentName);
}
