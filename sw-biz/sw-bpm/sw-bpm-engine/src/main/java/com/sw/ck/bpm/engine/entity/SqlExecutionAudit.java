package com.sw.ck.bpm.engine.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sw.ck.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * SQL 执行审计日志（主库表，继承 BaseEntity 走租户隔离）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sw_bpm_ext_sql_execution_audit")
public class SqlExecutionAudit extends BaseEntity {

    /** 外部数据源 ID */
    @TableField("datasource_id")
    private Long datasourceId;

    /** 外部数据源名称（冗余，便于审计查询） */
    @TableField("datasource_name")
    private String datasourceName;

    /** 执行的 SQL 原文 */
    @TableField("sql_text")
    private String sqlText;

    /** 返回行数 */
    @TableField("row_count")
    private Integer rowCount;

    /** 执行耗时（毫秒） */
    @TableField("execution_time_ms")
    private Long executionTimeMs;

    /** 执行结果：0=失败，1=成功 */
    @TableField("success")
    private Integer success;

    /** 错误信息（失败时记录） */
    @TableField("error_message")
    private String errorMessage;

    /** 操作人 ID */
    @TableField("operator_id")
    private Long operatorId;

    /** 操作人用户名 */
    @TableField("operator_name")
    private String operatorName;
}
