package com.sw.ck.workflow.mapper;

import com.sw.ck.common.mapper.BaseMapperX;
import com.sw.ck.workflow.entity.SqlExecutionAudit;
import org.apache.ibatis.annotations.Mapper;

/**
 * SQL 执行审计 Mapper。
 */
@Mapper
public interface SqlExecutionAuditMapper extends BaseMapperX<SqlExecutionAudit> {
}
