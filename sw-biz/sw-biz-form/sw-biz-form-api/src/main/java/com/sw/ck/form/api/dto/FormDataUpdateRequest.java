package com.sw.ck.form.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 表单记录更新请求 DTO。
 * <p>
 * 主表字段整量覆盖 + 乐观锁版本号 + 子表行按变动状态分流。
 * </p>
 *
 * <h3>字段说明</h3>
 * <ul>
 *   <li>{@code data} — 主表单条记录的全部用户字段值（整量，不含 id/审计列/version）</li>
 *   <li>{@code version} — 乐观锁版本号，前端从查详情获取并原样回传</li>
 *   <li>{@code subTableRows} — 子表变动行，key 为 TABLE 字段的逻辑名，
 *       value 为该子表的 {@link SubTableRowAction} 列表</li>
 * </ul>
 */
@Data
public class FormDataUpdateRequest implements Serializable {

    /** 主表字段整量数据（字段名 → 值，不含系统列） */
    private Map<String, Object> data;

    /** 乐观锁版本号 */
    private Long version;

    /** 子表变动行，key=TABLE字段逻辑名，value=该子表的行变动列表 */
    private Map<String, List<SubTableRowAction>> subTableRows;
}
