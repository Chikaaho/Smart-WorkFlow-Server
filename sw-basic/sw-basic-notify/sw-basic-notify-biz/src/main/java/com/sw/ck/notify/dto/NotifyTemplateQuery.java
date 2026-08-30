package com.sw.ck.notify.dto;

import com.sw.ck.common.page.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息模板分页查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class NotifyTemplateQuery extends PageParam {

    /** 模板代码/名称关键字（模糊匹配） */
    private String keyword;

    /** 启用状态过滤（null=全部） */
    private Boolean enabled;
}
