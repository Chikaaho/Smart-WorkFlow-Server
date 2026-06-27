package com.sw.ck.system.service;

import com.sw.ck.system.controller.AuthMenuVO;

import java.util.List;

/**
 * 系统菜单服务。
 * <p>
 * 负责菜单树的组装与过滤：
 * <ul>
 *   <li>超管（superAdmin=true）→ 旁路过滤，返回全量菜单树</li>
 *   <li>非超管 → 经 userId → sys_user_role → sys_role_menu → sys_menu 过滤</li>
 * </ul>
 * </p>
 */
public interface SysMenuService {

    /**
     * 获取当前用户的菜单树。
     *
     * @param userId     当前登录用户 ID
     * @param superAdmin 是否超管
     * @return 组装好的菜单树（根节点列表），无菜单时返回空列表
     */
    List<AuthMenuVO> getMenuTree(Long userId, boolean superAdmin);
}
