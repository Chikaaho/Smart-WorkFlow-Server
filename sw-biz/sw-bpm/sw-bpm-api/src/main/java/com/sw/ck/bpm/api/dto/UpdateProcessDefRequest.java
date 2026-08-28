package com.sw.ck.bpm.api.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 修改流程定义请求（仅 DRAFT 状态允许）。
 */
@Data
@Builder
public class UpdateProcessDefRequest {

    /**
     * 新流程名称（null 表示不修改）。
     */
    private String name;

    /**
     * 新绑定表单 formKey（null 表示不修改；变更时校验表单存在）。
     */
    private String formKey;
}
