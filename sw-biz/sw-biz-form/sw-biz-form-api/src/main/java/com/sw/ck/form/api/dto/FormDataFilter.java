package com.sw.ck.form.api.dto;

import lombok.Data;

/**
 * 表单数据查询过滤条件。
 *
 * <p>field 使用逻辑字段名（非物理列名），
 * 由后端经 {@code ColumnValidation.physicalColumnName()} 单出口转为物理列名。</p>
 */
@Data
public class FormDataFilter {

    /** 逻辑字段名 */
    private String field;

    /** 过滤操作符 */
    private FilterOp op;

    /** 过滤值（标量；IN 预留为数组，v1 拒） */
    private Object value;
}
