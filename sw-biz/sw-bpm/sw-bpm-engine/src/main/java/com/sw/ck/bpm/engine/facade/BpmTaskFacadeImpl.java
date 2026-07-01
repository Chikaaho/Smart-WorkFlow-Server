package com.sw.ck.bpm.engine.facade;

import com.sw.ck.bpm.api.dto.BpmTaskDTO;
import com.sw.ck.bpm.api.facade.BpmTaskFacade;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * BPM 任务门面实现 —— 封装 Flowable {@link TaskService} + {@link RuntimeService} + {@link RepositoryService} 查询。
 */
@Service
public class BpmTaskFacadeImpl implements BpmTaskFacade {

    private static final Logger log = LoggerFactory.getLogger(BpmTaskFacadeImpl.class);

    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final RepositoryService repositoryService;

    public BpmTaskFacadeImpl(TaskService taskService, RuntimeService runtimeService,
                             RepositoryService repositoryService) {
        this.taskService = taskService;
        this.runtimeService = runtimeService;
        this.repositoryService = repositoryService;
    }

    @Override
    public List<BpmTaskDTO> queryTodo(String tenantId, String assignee) {
        List<Task> tasks = taskService.createTaskQuery()
                .taskTenantId(tenantId)
                .taskAssignee(assignee)
                .list();

        return tasks.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<BpmTaskDTO> queryByProcessInstance(String processInstanceId) {
        List<Task> tasks = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .list();
        return tasks.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public BpmTaskDTO getTask(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            return null;
        }
        return toDto(task);
    }

    @Override
    public void complete(String taskId, Map<String, Object> variables) {
        if (variables != null && !variables.isEmpty()) {
            taskService.complete(taskId, variables);
        } else {
            taskService.complete(taskId);
        }
        log.info("BPM task completed: taskId={}", taskId);
    }

    @Override
    public boolean isProcessActive(String processInstanceId) {
        long count = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .count();
        return count > 0;
    }

    @Override
    public String getVariable(String processInstanceId, String name) {
        Object val = runtimeService.getVariable(processInstanceId, name);
        return val != null ? val.toString() : null;
    }

    @Override
    public String getBusinessKey(String processInstanceId) {
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (pi != null) {
            return pi.getBusinessKey();
        }
        // 流程已结束（无活动实例）时无法从 RuntimeService 获取 businessKey，
        // 返回 null 由 process 层按需扩展 HistoryService 查询。
        log.debug("Process instance not active or not found: processInstanceId={}", processInstanceId);
        return null;
    }

    // ==================== 内部方法 ====================

    private BpmTaskDTO toDto(Task task) {
        BpmTaskDTO dto = new BpmTaskDTO();
        dto.setTaskId(task.getId());
        dto.setName(task.getName());
        dto.setProcessInstanceId(task.getProcessInstanceId());
        dto.setProcessDefinitionKey(getProcessDefinitionKey(task));
        dto.setAssignee(task.getAssignee());
        dto.setCreateTime(task.getCreateTime());

        // businessKey 从 ProcessInstance 获取
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(task.getProcessInstanceId())
                .singleResult();
        if (pi != null) {
            dto.setBusinessKey(pi.getBusinessKey());
        }

        return dto;
    }

    /**
     * 从 Task 的 processDefinitionId 解析 process definition key。
     * <p>
     * Flowable Task 接口未直接暴露 processDefinitionKey，
     * 需通过 RepositoryService 查询 ProcessDefinition 获取。
     * </p>
     */
    private String getProcessDefinitionKey(Task task) {
        if (task.getProcessDefinitionId() == null) {
            return null;
        }
        ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(task.getProcessDefinitionId())
                .singleResult();
        return pd != null ? pd.getKey() : null;
    }
}
