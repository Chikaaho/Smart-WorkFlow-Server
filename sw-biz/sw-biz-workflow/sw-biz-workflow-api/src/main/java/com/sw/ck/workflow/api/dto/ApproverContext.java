package com.sw.ck.workflow.api.dto;

import lombok.Data;

import java.util.Map;

/**
 * 审批人解析上下文。
 * <p>
 * 在流程发起时由 {@link com.sw.ck.workflow.api.spi.ApproverResolver#resolve(ApproverContext)}
 * 读取，用于确定当前流程节点的审批人。
 * </p>
 */
@Data
public class ApproverContext {

    /** 发起时提交的表单 key */
    private String formKey;

    /** 表单提交数据（字段名 → 值） */
    private Map<String, Object> submittedData;

    /** 发起人用户 ID */
    private Long submitter;

    /** 当前租户 ID */
    private Long tenantId;
}
