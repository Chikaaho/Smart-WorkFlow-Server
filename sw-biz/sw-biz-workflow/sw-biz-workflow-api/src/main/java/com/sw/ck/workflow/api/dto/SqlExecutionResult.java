package com.sw.ck.workflow.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * SQL 执行结果。
 */
@Data
@Builder
public class SqlExecutionResult {

    /** 列名列表 */
    private List<String> columns;

    /** 数据行（每行为列名 → 值的 Map） */
    private List<Map<String, Object>> rows;

    /** 返回行数 */
    private int rowCount;

    /** 执行耗时（毫秒） */
    private long executionTimeMs;
}
