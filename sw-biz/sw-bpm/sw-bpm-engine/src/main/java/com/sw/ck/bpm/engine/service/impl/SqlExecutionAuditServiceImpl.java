package com.sw.ck.bpm.engine.service.impl;

import com.sw.ck.bpm.engine.entity.SqlExecutionAudit;
import com.sw.ck.bpm.engine.mapper.SqlExecutionAuditMapper;
import com.sw.ck.bpm.engine.service.SqlExecutionAuditService;
import com.sw.ck.common.service.BaseServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SQL 执行审计 Service 实现。
 */
@Service
public class SqlExecutionAuditServiceImpl
        extends BaseServiceImpl<SqlExecutionAuditMapper, SqlExecutionAudit>
        implements SqlExecutionAuditService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditSuccess(Long datasourceId, String datasourceName, String sql,
                             int rowCount, long executionTimeMs, Long operatorId, String operatorName) {
        SqlExecutionAudit audit = new SqlExecutionAudit();
        audit.setDatasourceId(datasourceId);
        audit.setDatasourceName(datasourceName);
        audit.setSqlText(sql);
        audit.setRowCount(rowCount);
        audit.setExecutionTimeMs(executionTimeMs);
        audit.setSuccess(1);
        audit.setOperatorId(operatorId);
        audit.setOperatorName(operatorName);
        save(audit);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditFailure(Long datasourceId, String datasourceName, String sql,
                             long executionTimeMs, String errorMessage, Long operatorId, String operatorName) {
        SqlExecutionAudit audit = new SqlExecutionAudit();
        audit.setDatasourceId(datasourceId);
        audit.setDatasourceName(datasourceName);
        audit.setSqlText(sql);
        audit.setRowCount(0);
        audit.setExecutionTimeMs(executionTimeMs);
        audit.setSuccess(0);
        audit.setErrorMessage(errorMessage);
        audit.setOperatorId(operatorId);
        audit.setOperatorName(operatorName);
        save(audit);
    }
}
