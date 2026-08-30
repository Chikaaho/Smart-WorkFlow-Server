package com.sw.ck.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sw.ck.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典数据表（sys_dict_data）。
 * <p>
 * 字典条目，通过 {@code dict_code} 引用 {@link SysDictType#code} 关联所属字典类型。
 * 每一条记录对应一个"值→标签"映射。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_data")
public class SysDictData extends BaseEntity {

    /** 所属字典类型编码（引用 sys_dict_type.code） */
    @TableField("dict_code")
    private String dictCode;

    /** 字典标签（显示名） */
    @TableField("label")
    private String label;

    /** 字典值 */
    @TableField("dict_value")
    private String dictValue;

    /** 排序号（升序） */
    @TableField("sort")
    private Integer sort;

    /** 状态：0=正常 1=停用 */
    @TableField("status")
    private Integer status;

    /** 是否默认：0=否 1=是 */
    @TableField("is_default")
    private Integer isDefault;

    /** CSS 类名 */
    @TableField("css_class")
    private String cssClass;

    /** 列表类名 */
    @TableField("list_class")
    private String listClass;

    /** 描述 */
    @TableField("description")
    private String description;
}
