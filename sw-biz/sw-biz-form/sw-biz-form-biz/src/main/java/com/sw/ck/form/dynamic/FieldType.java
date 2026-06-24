package com.sw.ck.form.dynamic;

/**
 * 表单字段类型枚举。
 * <p>
 * 用于描述动态宽表的字段语义，进而映射到目标数据库的物理列类型。
 * 映射关系定义在 {@link VendorDialect#columnType(FieldType)} 中。
 * </p>
 *
 * <ul>
 *   <li>{@link #TEXT} — 文本（短文本）</li>
 *   <li>{@link #RICH_TEXT} — 富文本</li>
 *   <li>{@link #NUMBER} — 数字</li>
 *   <li>{@link #DATE} — 日期</li>
 *   <li>{@link #BOOL} — 布尔</li>
 *   <li>{@link #DICT} — 字典（存 dict_value 字符串）</li>
 *   <li>{@link #REFERENCE} — 关联（bigint 外键，存目标表单记录 id）</li>
 *   <li>{@link #TABLE} — 表格子表（非列类型，触发 {@code sw_form_table_} 子表创建）</li>
 * </ul>
 */
public enum FieldType {

    TEXT,
    RICH_TEXT,
    NUMBER,
    DATE,
    BOOL,
    DICT,
    REFERENCE,
    TABLE

}
