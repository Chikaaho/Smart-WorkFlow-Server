package com.sw.ck.lowcode.api.event;

import lombok.Getter;

import java.io.Serializable;
import java.util.Map;

/**
 * 表单提交事件。
 * <p>
 * 在 sw-biz-lowcode-biz 的提交逻辑中 publishEvent，
 * 由 sw-biz-workflow-biz 通过 @EventListener 监听，决定是否发起流程。
 * <p>
 * 定义在 -api 模块，确保 lowcode 不直接依赖 workflow。
 */
@Getter
public class FormSubmittedEvent implements Serializable {

    /**
     * 表单标识（formKey）
     */
    private final String formKey;

    /**
     * 提交数据（控件 name → 值）
     */
    private final Map<String, Object> submittedData;

    /**
     * 提交人用户 ID
     */
    private final String submitter;

    public FormSubmittedEvent(String formKey, Map<String, Object> submittedData, String submitter) {
        this.formKey = formKey;
        this.submittedData = submittedData;
        this.submitter = submitter;
    }
}
