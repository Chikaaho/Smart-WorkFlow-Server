package com.sw.ck.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sw.ck.system.controller.AuthMenuVO;
import com.sw.ck.system.entity.SysMenu;
import com.sw.ck.system.entity.SysRoleMenu;
import com.sw.ck.system.entity.SysUserRole;
import com.sw.ck.system.mapper.SysMenuMapper;
import com.sw.ck.system.mapper.SysRoleMenuMapper;
import com.sw.ck.system.mapper.SysUserRoleMapper;
import com.sw.ck.system.service.SysMenuService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 菜单树服务实现。
 * <p>
 * 树形组装算法：先按 sort 升序排列全量，再用 {@code parent_id=0} 识别根节点，
 * 通过 Map{@code <id, VO>} 将子节点挂到父节点 children 下。
 * VO 边界转换（id/parentId String 化、component 目录按钮置 null）在 {@link #toVo(SysMenu)} 中完成。
 * </p>
 */
@Service
public class SysMenuServiceImpl implements SysMenuService {

    private final SysMenuMapper sysMenuMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;

    public SysMenuServiceImpl(SysMenuMapper sysMenuMapper,
                              SysUserRoleMapper sysUserRoleMapper,
                              SysRoleMenuMapper sysRoleMenuMapper) {
        this.sysMenuMapper = sysMenuMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.sysRoleMenuMapper = sysRoleMenuMapper;
    }

    @Override
    public List<AuthMenuVO> getMenuTree(Long userId, boolean superAdmin) {
        List<SysMenu> menus;

        if (superAdmin) {
            // 超管旁路：返回全量菜单（未删除行），按 sort 升序
            menus = sysMenuMapper.selectList(
                    Wrappers.lambdaQuery(SysMenu.class)
                            .orderByAsc(SysMenu::getSort));
        } else {
            // 非超管：经用户角色 → 角色菜单 → 菜单过滤
            List<Long> menuIds = loadMenuIdsByUserId(userId);
            if (menuIds.isEmpty()) {
                return Collections.emptyList();
            }
            menus = sysMenuMapper.selectList(
                    Wrappers.lambdaQuery(SysMenu.class)
                            .in(SysMenu::getId, menuIds)
                            .orderByAsc(SysMenu::getSort));
        }

        if (menus.isEmpty()) {
            return Collections.emptyList();
        }

        return buildTree(menus);
    }

    /**
     * 加载非超管用户有权访问的菜单 ID 列表。
     * 路径：sys_user_role → sys_role_menu → sys_menu.id
     */
    private List<Long> loadMenuIdsByUserId(Long userId) {
        // 1. 查用户关联的角色
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
                Wrappers.lambdaQuery(SysUserRole.class)
                        .eq(SysUserRole::getUserId, userId));
        if (userRoles.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> roleIds = userRoles.stream()
                .map(SysUserRole::getRoleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 2. 查角色关联的菜单
        List<SysRoleMenu> roleMenus = sysRoleMenuMapper.selectList(
                Wrappers.lambdaQuery(SysRoleMenu.class)
                        .in(SysRoleMenu::getRoleId, roleIds));
        if (roleMenus.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. 去重后返回菜单 ID
        return roleMenus.stream()
                .map(SysRoleMenu::getMenuId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 将平铺菜单列表组装为树形结构。
     * <p>
     * 输入已按 sort 升序，所以各层级自然有序。
     * </p>
     *
     * @param menus 已排序的平铺菜单列表
     * @return 根节点列表（parent_id=0 的节点）
     */
    private List<AuthMenuVO> buildTree(List<SysMenu> menus) {
        // 转换为 VO 并建立 id → VO 映射
        Map<Long, AuthMenuVO> nodeMap = new LinkedHashMap<>(menus.size());
        List<AuthMenuVO> roots = new ArrayList<>();

        for (SysMenu menu : menus) {
            AuthMenuVO vo = toVo(menu);
            nodeMap.put(menu.getId(), vo);

            if (menu.getParentId() == null || menu.getParentId() == 0) {
                roots.add(vo);
            }
        }

        // 挂载非根节点到父节点
        for (SysMenu menu : menus) {
            if (menu.getParentId() != null && menu.getParentId() != 0) {
                AuthMenuVO parent = nodeMap.get(menu.getParentId());
                if (parent != null) {
                    List<AuthMenuVO> children = new ArrayList<>(parent.getChildren());
                    children.add(nodeMap.get(menu.getId()));
                    parent.setChildren(children);
                }
            }
        }

        return roots;
    }

    /**
     * SysMenu → AuthMenuVO 边界转换。
     * <p>
     * 转换规则：
     * <ul>
     *   <li>id：bigint → String</li>
     *   <li>parentId：parent_id=0 或 null → null；其它 → String</li>
     *   <li>component：menu_type=0（目录）或 menu_type=2（按钮）→ null</li>
     * </ul>
     * </p>
     */
    private AuthMenuVO toVo(SysMenu menu) {
        String parentId = (menu.getParentId() == null || menu.getParentId() == 0L)
                ? null
                : String.valueOf(menu.getParentId());

        String component = (menu.getMenuType() == null
                || menu.getMenuType() == 0
                || menu.getMenuType() == 2)
                ? null
                : menu.getComponent();

        return AuthMenuVO.builder()
                .id(String.valueOf(menu.getId()))
                .parentId(parentId)
                .name(menu.getName())
                .title(menu.getTitle())
                .path(menu.getPath())
                .component(component)
                .icon(menu.getIcon())
                .sort(menu.getSort())
                .menuType(menu.getMenuType())
                .permission(menu.getPermission())
                .hidden(menu.getHidden())
                .build();
    }
}
