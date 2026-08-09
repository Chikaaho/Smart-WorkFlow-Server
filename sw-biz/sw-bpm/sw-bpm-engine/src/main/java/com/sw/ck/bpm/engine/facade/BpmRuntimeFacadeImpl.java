package com.sw.ck.bpm.engine.facade;

import com.sw.ck.bpm.api.dto.BpmActivityDTO;
import com.sw.ck.bpm.api.facade.BpmRuntimeFacade;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * BPM 运行时门面实现 —— 封装 Flowable {@link RuntimeService} + {@link HistoryService}。
 */
@Service
public class BpmRuntimeFacadeImpl implements BpmRuntimeFacade {

    private static final Logger log = LoggerFactory.getLogger(BpmRuntimeFacadeImpl.class);

    private final RuntimeService runtimeService;
    private final HistoryService historyService;

    public BpmRuntimeFacadeImpl(RuntimeService runtimeService, HistoryService historyService) {
        this.runtimeService = runtimeService;
        this.historyService = historyService;
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

    @Override
    public List<String> getActiveActivityIds(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return List.of();
        }
        try {
            List<String> ids = runtimeService.getActiveActivityIds(processInstanceId);
            return ids != null ? ids : List.of();
        } catch (Exception e) {
            log.warn("Failed to get active activity ids: processInstanceId={}, error={}",
                    processInstanceId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<BpmActivityDTO> queryHistoricActivities(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return List.of();
        }
        try {
            List<org.flowable.engine.history.HistoricActivityInstance> activities =
                    historyService.createHistoricActivityInstanceQuery()
                            .processInstanceId(processInstanceId)
                            .orderByHistoricActivityInstanceEndTime().asc()
                            .list();

            if (activities == null || activities.isEmpty()) {
                return List.of();
            }

            return activities.stream()
                    .map(this::toActivityDto)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to query historic activities: processInstanceId={}, error={}",
                    processInstanceId, e.getMessage());
            return List.of();
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 将 Flowable {@code HistoricActivityInstance} 转为我方 {@link BpmActivityDTO}。
     * <p>
     * 时间转换：使用系统默认时区 {@code LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault())}，
     * 与 {@code BpmTodoController.toTodoTaskDTO} 模式一致。
     * </p>
     */
    private BpmActivityDTO toActivityDto(
            org.flowable.engine.history.HistoricActivityInstance ha) {
        BpmActivityDTO dto = new BpmActivityDTO();
        dto.setActivityId(ha.getActivityId());
        dto.setActivityName(ha.getActivityName());
        dto.setActivityType(ha.getActivityType());
        if (ha.getStartTime() != null) {
            dto.setStartTime(LocalDateTime.ofInstant(
                    ha.getStartTime().toInstant(), ZoneId.systemDefault()));
        }
        if (ha.getEndTime() != null) {
            dto.setEndTime(LocalDateTime.ofInstant(
                    ha.getEndTime().toInstant(), ZoneId.systemDefault()));
        }
        dto.setAssignee(ha.getAssignee());
        dto.setTaskId(ha.getTaskId());
        return dto;
    }
}
