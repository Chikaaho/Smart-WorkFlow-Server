package com.sw.ck.bpm.api.facade;

import com.sw.ck.bpm.api.dto.BpmTaskDTO;

import java.util.List;
import java.util.Map;

/**
 * BPM 任务门面 —— 封装流程引擎 TaskService + 部分 RuntimeService 查询。
 * <p>
 * 定义待办查询、任务完成、流程状态查询等操作契约。
 * 实现类位于 sw-bpm-engine（闭源），由 Spring 注入。
 * </p>
 *
 * @since 1.0.0
 */
public interface BpmTaskFacade {

    /**
     * 查询待办任务列表。
     *
     * @param tenantId 租户 ID
     * @param assignee 处理人
     * @return 待办任务列表
     */
    List<BpmTaskDTO> queryTodo(String tenantId, String assignee);

    /**
     * 按流程实例 id 精确查询该实例下的任务（发起后取任务、实例任务列表用）。
     *
     * @param processInstanceId 流程实例 ID
     * @return 任务列表
     */
    List<BpmTaskDTO> queryByProcessInstance(String processInstanceId);

    /**
     * 获取单个任务详情。
     *
     * @param taskId 任务 ID
     * @return 任务 DTO
     */
    BpmTaskDTO getTask(String taskId);

    /**
     * 完成任务。
     *
     * @param taskId    任务 ID
     * @param variables 流程变量
     */
    void complete(String taskId, Map<String, Object> variables);

    /**
     * 判断流程实例是否活跃。
     *
     * @param processInstanceId 流程实例 ID
     * @return true 表示流程仍在运行
     */
    boolean isProcessActive(String processInstanceId);

    /**
     * 获取流程变量。
     *
     * @param processInstanceId 流程实例 ID
     * @param name              变量名
     * @return 变量值字符串
     */
    String getVariable(String processInstanceId, String name);

    /**
     * 获取流程实例的业务键。
     *
     * @param processInstanceId 流程实例 ID
     * @return 业务键
     */
    String getBusinessKey(String processInstanceId);
}
