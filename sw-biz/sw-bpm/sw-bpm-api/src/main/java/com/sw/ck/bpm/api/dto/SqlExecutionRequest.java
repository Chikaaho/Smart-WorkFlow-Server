package com.sw.ck.bpm.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * SQL 执行请求。
 */
@Data
public class SqlExecutionRequest {

    /** 外部数据源 ID */
    @NotNull(message = "datasourceId 不能为空")
    private Long datasourceId;

    /** 待执行的 SQL（仅允许单条 SELECT） */
    @NotBlank(message = "sql 不能为空")
    private String sql;
}
