package com.sw.ck.bpm.process.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sw.ck.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表单↔流程绑定实体。
 * <p>
 * 将表单（by form_key）绑定到一条 BPMN 流程定义（by process_def_key）。
 * 同租户下同表单最多一条启用绑定（{@code active=true}），由唯一索引 {@code uk_sw_bpm_binding_active} 保证。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sw_bpm_form_binding")
public class BpmFormBinding extends BaseEntity {

    /**
     * 表单业务标识（对应 {@code FormSubmittedEvent.formKey}）。
     */
    @TableField("form_key")
    private String formKey;

    /**
     * BPMN 流程定义 key（Flowable 部署时使用）。
     */
    @TableField("process_def_key")
    private String processDefKey;

    /**
     * 是否启用于发起流程的唯一绑定。
     */
    @TableField("active")
    private Boolean active;
}
