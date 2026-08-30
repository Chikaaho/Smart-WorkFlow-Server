package com.sw.ck.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sw.ck.common.entity.BaseEntityNoTenant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统菜单表（全局表，无租户列）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends BaseEntityNoTenant {

    /** 父菜单 ID */
    @TableField("parent_id")
    private Long parentId;

    /** 菜单名称 */
    @TableField("name")
    private String name;

    /** 菜单标题（显示文本） */
    @TableField("title")
    private String title;

    /** 路由路径 */
    @TableField("path")
    private String path;

    /** 组件路径 */
    @TableField("component")
    private String component;

    /** 图标 */
    @TableField("icon")
    private String icon;

    /** 排序 */
    @TableField("sort")
    private Integer sort;

    /** 菜单类型：0=目录 1=菜单 2=按钮 */
    @TableField("menu_type")
    private Integer menuType;

    /** 权限标识 */
    @TableField("permission")
    private String permission;

    /** 是否隐藏：true=隐藏 false=显示 */
    @TableField("hidden")
    private Boolean hidden;
}
