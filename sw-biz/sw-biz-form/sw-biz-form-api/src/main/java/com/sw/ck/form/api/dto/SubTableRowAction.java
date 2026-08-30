package com.sw.ck.form.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 子表行变动 DTO（更新端点使用）。
 * <p>
 * 前端为子表每行打标变动状态，后端按状态分流执行 INSERT / UPDATE / 软删 / 跳过。
 * </p>
 *
 * <h3>action 字面量</h3>
 * <ul>
 *   <li>{@code ADD} — 新增行，id 可为 null（后端生成 UUID）</li>
 *   <li>{@code UPDATE} — 修改行，id 必填</li>
 *   <li>{@code DELETE} — 删除行（软删），id 必填</li>
 *   <li>{@code UNCHANGED} — 未变动，跳过，id 必填</li>
 * </ul>
 */
@Data
public class SubTableRowAction implements Serializable {

    /** 行变动状态：ADD / UPDATE / DELETE / UNCHANGED */
    private String action;

    /** 行 ID（ADD 时可 null，UPDATE / DELETE / UNCHANGED 必须传） */
    private String id;

    /** 行字段值（ADD / UPDATE 时传，DELETE / UNCHANGED 可空） */
    private Map<String, Object> data;
}
