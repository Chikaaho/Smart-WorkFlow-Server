package com.sw.ck.system.controller;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * GET /system/auth/menus 响应 VO（菜单树节点）。
 * <p>
 * 对齐前端菜单树契约，字段类型与库表不一致之处在此层转换：
 * <ul>
 *   <li>id / parentId：bigint → String</li>
 *   <li>parentId == 0（根节点）→ null</li>
 *   <li>component：目录(menu_type=0)或按钮(menu_type=2) → null</li>
 * </ul>
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthMenuVO {

    /** 菜单 ID（bigint → string） */
    private String id;

    /** 父菜单 ID；根节点（原 parent_id=0）输出 null */
    private String parentId;

    /** 菜单名称（路由 name） */
    private String name;

    /** 菜单标题（显示文本） */
    private String title;

    /** 路由路径 */
    private String path;

    /** 组件路径；目录(menu_type=0)或按钮(menu_type=2)输出 null */
    private String component;

    /** 图标 */
    private String icon;

    /** 排序（同级升序） */
    private Integer sort;

    /** 菜单类型：0=目录 1=菜单 2=按钮 */
    private Integer menuType;

    /** 权限标识 */
    private String permission;

    /** 是否隐藏 */
    private Boolean hidden;

    /** 子菜单 */
    @Builder.Default
    private List<AuthMenuVO> children = List.of();
}
