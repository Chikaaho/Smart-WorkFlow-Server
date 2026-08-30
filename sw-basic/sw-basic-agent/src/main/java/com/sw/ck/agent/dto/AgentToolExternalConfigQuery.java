package com.sw.ck.agent.dto;

import com.sw.ck.common.page.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 外部工具分页查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentToolExternalConfigQuery extends PageParam {

    /** 工具名关键字（模糊匹配） */
    private String nameKeyword;

    /** 启用状态过滤（null=全部） */
    private Boolean enabled;
}
