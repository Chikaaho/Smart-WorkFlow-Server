package com.sw.ck.bpm.engine.mapper;

import com.sw.ck.bpm.engine.entity.SqlExecutionAudit;
import com.sw.ck.common.mapper.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

/**
 * SQL 执行审计 Mapper。
 */
@Mapper
public interface SqlExecutionAuditMapper extends BaseMapperX<SqlExecutionAudit> {
}
