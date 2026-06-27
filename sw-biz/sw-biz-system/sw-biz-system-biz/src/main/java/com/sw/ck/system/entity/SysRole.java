package com.sw.ck.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sw.ck.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统角色表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    /** 角色名称 */
    @TableField("name")
    private String name;

    /** 角色标识（返回前端的 role key） */
    @TableField("code")
    private String code;

    /** 排序 */
    @TableField("sort")
    private Integer sort;

    /** 状态：1=启用 0=停用 */
    @TableField("status")
    private Integer status;

    /** 数据范围（S7 预留，当前不生效） */
    @TableField("data_scope")
    private Integer dataScope;

    /** 内置标记 */
    @TableField("built_in")
    private Boolean builtIn;

    /** 备注 */
    @TableField("remark")
    private String remark;
}
