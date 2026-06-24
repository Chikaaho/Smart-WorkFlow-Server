package com.sw.ck.form.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 更新表单草稿请求。
 */
@Data
public class FormUpdateReq implements Serializable {

    /** 表单名称 */
    private String name;

    /** 用户自定义逻辑表名 */
    private String logicalTableName;

    /** 表单描述 */
    private String description;
}
