package com.sw.ck.workflow.service;

import com.sw.ck.common.service.BaseService;
import com.sw.ck.workflow.entity.SqlExecutionAudit;

/**
 * SQL 执行审计 Service。
 */
public interface SqlExecutionAuditService extends BaseService<SqlExecutionAudit> {

    /**
     * 记录执行成功的审计日志。
     */
    void auditSuccess(Long datasourceId, String datasourceName, String sql,
                      int rowCount, long executionTimeMs, Long operatorId, String operatorName);

    /**
     * 记录执行失败的审计日志。
     */
    void auditFailure(Long datasourceId, String datasourceName, String sql,
                      long executionTimeMs, String errorMessage, Long operatorId, String operatorName);
}
