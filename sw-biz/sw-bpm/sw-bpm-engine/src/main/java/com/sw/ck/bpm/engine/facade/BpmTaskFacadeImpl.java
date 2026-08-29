package com.sw.ck.bpm.engine.facade;

import com.sw.ck.bpm.api.dto.BpmTaskDTO;
import com.sw.ck.bpm.api.facade.BpmTaskFacade;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.history.HistoricTaskInstance;
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
    private final HistoryService historyService;

    public BpmTaskFacadeImpl(TaskService taskService, RuntimeService runtimeService,
                             RepositoryService repositoryService, HistoryService historyService) {
        this.taskService = taskService;
        this.runtimeService = runtimeService;
        this.repositoryService = repositoryService;
        this.historyService = historyService;
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
    public List<BpmTaskDTO> queryTodoPage(String tenantId, String assignee, int offset, int limit) {
        List<Task> tasks = taskService.createTaskQuery()
                .taskTenantId(tenantId)
                .taskAssignee(assignee)
                .orderByTaskCreateTime().desc()
                .listPage(offset, limit);

        return tasks.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public long countTodo(String tenantId, String assignee) {
        return taskService.createTaskQuery()
                .taskTenantId(tenantId)
                .taskAssignee(assignee)
                .count();
    }

    @Override
    public Map<String, Object> getVariables(String processInstanceId) {
        return runtimeService.getVariables(processInstanceId);
    }

    @Override
    public List<BpmTaskDTO> queryProcessedPage(String tenantId, String assignee, int offset, int limit) {
        List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
                .taskTenantId(tenantId)
                .taskAssignee(assignee)
                .finished()
                .orderByHistoricTaskInstanceEndTime().desc()
                .listPage(offset, limit);

        return tasks.stream()
                .map(this::toDtoFromHistory)
                .collect(Collectors.toList());
    }

    @Override
    public long countProcessed(String tenantId, String assignee) {
        return historyService.createHistoricTaskInstanceQuery()
                .taskTenantId(tenantId)
                .taskAssignee(assignee)
                .finished()
                .count();
    }

    @Override
    public List<BpmTaskDTO> queryHistoryByProcessInstance(String processInstanceId) {
        List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .finished()
                .orderByHistoricTaskInstanceEndTime().desc()
                .list();

        // 兜底：本引擎版本在 create 监听器内 setAssignee 不落 HI_TASKINST.ASSIGNEE_，
        // 用历史流程变量 approver（DESIGNATED 指定审批人，v1 单审批人语义下与实际 assignee
        // 一致）补齐，否则审批历史审批人显示 "-"（R-04 缺口）。
        String approverFallback = resolveApproverVariable(processInstanceId);

        return tasks.stream()
                .map(this::toDtoFromHistory)
                .peek(dto -> {
                    if ((dto.getAssignee() == null || dto.getAssignee().isBlank())
                            && approverFallback != null) {
                        dto.setAssignee(approverFallback);
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * 读取历史流程变量 approver（DESIGNATED 审批人配置，String 或 List 取首元素）。
     * 查询失败返回 null，不阻断审批历史查询。
     */
    private String resolveApproverVariable(String processInstanceId) {
        try {
            org.flowable.variable.api.history.HistoricVariableInstance var = historyService
                    .createHistoricVariableInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .variableName("approver")
                    .singleResult();
            if (var == null || var.getValue() == null) {
                return null;
            }
            Object v = var.getValue();
            if (v instanceof java.util.Collection<?> col) {
                return col.isEmpty() ? null : String.valueOf(col.iterator().next());
            }
            return String.valueOf(v);
        } catch (Exception e) {
            return null;
        }
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
        try {
            Object val = runtimeService.getVariable(processInstanceId, name);
            if (val != null) {
                return val.toString();
            }
        } catch (org.flowable.common.engine.api.FlowableObjectNotFoundException e) {
            // 流程已结束：运行时实例被清空，回落历史变量（已办任务列表需要读取结束实例的 formKey）
        }
        org.flowable.variable.api.history.HistoricVariableInstance hv = historyService
                .createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .variableName(name)
                .singleResult();
        return hv != null && hv.getValue() != null ? hv.getValue().toString() : null;
    }

    @Override
    public String getBusinessKey(String processInstanceId) {
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (pi != null) {
            return pi.getBusinessKey();
        }
        // 流程已结束（无活动实例）：运行时实例被清空，回落历史实例的 businessKey
        // （已办任务列表需要展示结束实例的业务单号）
        org.flowable.engine.history.HistoricProcessInstance hpi = historyService
                .createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        return hpi != null ? hpi.getBusinessKey() : null;
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
        return getProcessDefinitionKeyFromId(task.getProcessDefinitionId());
    }

    /**
     * 从 processDefinitionId 解析 process definition key。
     * <p>
     * 提取为公共方法，供 {@link #toDto(Task)} 和 {@link #toDtoFromHistory(HistoricTaskInstance)} 共用。
     * </p>
     */
    private String getProcessDefinitionKeyFromId(String processDefinitionId) {
        if (processDefinitionId == null) {
            return null;
        }
        ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId)
                .singleResult();
        return pd != null ? pd.getKey() : null;
    }

    /**
     * 将 Flowable HistoricTaskInstance 转为 BpmTaskDTO。
     * <p>
     * 与 {@link #toDto(Task)} 的区别：增加 endTime 字段。
     * </p>
     */
    private BpmTaskDTO toDtoFromHistory(HistoricTaskInstance task) {
        BpmTaskDTO dto = new BpmTaskDTO();
        dto.setTaskId(task.getId());
        dto.setName(task.getName());
        dto.setProcessInstanceId(task.getProcessInstanceId());
        dto.setProcessDefinitionKey(getProcessDefinitionKeyFromId(task.getProcessDefinitionId()));
        dto.setAssignee(task.getAssignee());
        dto.setCreateTime(task.getCreateTime());
        dto.setEndTime(task.getEndTime());
        return dto;
    }
}
