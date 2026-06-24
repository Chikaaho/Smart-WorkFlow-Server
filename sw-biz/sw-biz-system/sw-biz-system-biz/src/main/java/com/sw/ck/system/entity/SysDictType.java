package com.sw.ck.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sw.ck.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典类型表（sys_dict_type）。
 * <p>
 * 定义字典类型的元数据，每种字典类型由唯一 {@code code} 标识。
 * 字典数据项存储在 {@link SysDictData} 中，通过 {@code dict_code} 关联本表的 {@code code}。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_type")
public class SysDictType extends BaseEntity {

    /** 字典类型名称（如"通用状态"） */
    @TableField("name")
    private String name;

    /** 字典类型编码（如 sys_common_status），全局唯一 */
    @TableField("code")
    private String code;

    /** 状态：0=正常 1=停用 */
    @TableField("status")
    private Integer status;

    /** 描述 */
    @TableField("description")
    private String description;
}
