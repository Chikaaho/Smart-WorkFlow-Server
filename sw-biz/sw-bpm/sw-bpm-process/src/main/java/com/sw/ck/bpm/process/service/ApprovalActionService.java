package com.sw.ck.bpm.process.service;

import com.sw.ck.bpm.process.entity.ApprovalActionRecord;
import com.sw.ck.common.service.BaseService;

import java.util.List;

public interface ApprovalActionService extends BaseService<ApprovalActionRecord> {
    List<ApprovalActionRecord> findByProcessInstanceId(String processInstanceId);
    /** 查询任务已落库的动作，用于重复提交返回确定性业务错误。 */
    ApprovalActionRecord findByTaskId(String taskId);
    boolean existsForTask(String taskId);
}
