package com.sw.ck.form.dynamic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 表单动态宽表的创建规格。
 * <p>
 * 描述"要建什么表（主表或子表）、哪些字段/列、什么类型"。
 * 由 {@link DynamicTableManager#createFormTable(FormTableSpec)} 消费。
 * </p>
 *
 * <pre>{@code
 * // 一张含文本、数字、字典、关联字段的主表
 * FormTableSpec spec = new FormTableSpec(false, List.of(
 *     FieldSpec.text("full_name"),
 *     FieldSpec.number("age"),
 *     FieldSpec.dict("gender", "sys_user_sex"),
 *     FieldSpec.ref("dept", "department_form")
 * ));
 * }</pre>
 *
 * @see FieldSpec
 * @see DynamicTableManager
 */
public class FormTableSpec {

    /** 是否为子表（true → 表名使用 {@code sw_form_table_} 前缀） */
    private final boolean subTable;

    /** 字段列表（TABLE 类型字段不生成列，改为创建子表） */
    private final List<FieldSpec> fields;

    /**
     * @param subTable 是否为子表
     * @param fields   字段规格列表
     */
    public FormTableSpec(boolean subTable, List<FieldSpec> fields) {
        this.subTable = subTable;
        this.fields = Collections.unmodifiableList(
                new ArrayList<>(Objects.requireNonNull(fields, "fields must not be null")));
    }

    public boolean isSubTable() {
        return subTable;
    }

    public List<FieldSpec> getFields() {
        return fields;
    }
}
