package com.sw.ck.form.api.dto;

import com.sw.ck.common.page.PageParam;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 表单数据查询请求。
 *
 * <p>继承 {@link PageParam} 统一使用 pageNum/pageSize 分页字段。
 * 响应复用 {@link com.sw.ck.common.page.PageResult}，与字典分页同形状。</p>
 */
@Getter
@Setter
public class FormDataQueryRequest extends PageParam {

    /** 过滤条件列表（null/空 = 不过滤） */
    private List<FormDataFilter> filters;
}
