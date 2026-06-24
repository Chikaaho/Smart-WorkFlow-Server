package com.sw.ck.form.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 表单定义渲染请求。
 * <p>
 * 前端设计器在更新表单配置时提交 definition JSON。
 * </p>
 */
@Data
public class FormConfigSaveReq implements Serializable {

    /** 表单配置 JSON（控件布局/样式/校验规则） */
    private String definition;
}
