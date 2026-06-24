package com.sw.ck.form.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表单配置/样式实体 — 对应 {@code sw_form_config} 表。
 * <p>
 * definition 字段存储表单的控件布局、样式、校验规则等完整 schema（JSON）。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sw_form_config")
public class FormConfigEntity extends FormBaseEntity {

    /** 关联 sw_form_def.id */
    private String formId;

    /** 表单定义 JSON（控件布局/样式/校验规则） */
    private String definition;
}
