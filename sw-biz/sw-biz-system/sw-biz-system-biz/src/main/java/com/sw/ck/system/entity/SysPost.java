package com.sw.ck.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sw.ck.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 岗位表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_post")
public class SysPost extends BaseEntity {

    /** 岗位编码 */
    @TableField("code")
    private String code;

    /** 岗位名称 */
    @TableField("name")
    private String name;

    /** 排序号 */
    @TableField("sort")
    private Integer sort;

    /** 状态：1=启用 0=停用 */
    @TableField("status")
    private Integer status;

    /** 备注/描述 */
    @TableField("description")
    private String description;
}
