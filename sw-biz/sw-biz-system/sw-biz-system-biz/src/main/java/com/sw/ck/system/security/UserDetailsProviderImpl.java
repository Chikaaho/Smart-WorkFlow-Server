package com.sw.ck.system.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sw.ck.security.holder.DataScope;
import com.sw.ck.security.holder.LoginUser;
import com.sw.ck.security.spi.UserDetailsProvider;
import com.sw.ck.system.entity.SysMenu;
import com.sw.ck.system.entity.SysRole;
import com.sw.ck.system.entity.SysRoleMenu;
import com.sw.ck.system.entity.SysUser;
import com.sw.ck.system.entity.SysUserRole;
import com.sw.ck.system.mapper.SysMenuMapper;
import com.sw.ck.system.mapper.SysRoleMapper;
import com.sw.ck.system.mapper.SysRoleMenuMapper;
import com.sw.ck.system.mapper.SysUserRoleMapper;
import com.sw.ck.system.service.SysUserService;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * sw-security SPI 实现：从组织架构 SysUser 表加载用户认证信息。
 * <p>
 * roles = 用户经 sys_user_role 关联的已启用 sys_role.code 集合；
 * superAdmin = roles 是否包含 'superadmin'；
 * permissions = superAdmin 时为空数组（旁路），否则经 sys_role_menu→sys_menu 取
 * menu_type=2 的 permission 标识。
 * </p>
 */
public class UserDetailsProviderImpl implements UserDetailsProvider {

    private static final String SUPER_ADMIN_ROLE_CODE = "superadmin";

    private final SysUserService sysUserService;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysMenuMapper sysMenuMapper;

    public UserDetailsProviderImpl(SysUserService sysUserService,
                                   SysUserRoleMapper sysUserRoleMapper,
                                   SysRoleMapper sysRoleMapper,
                                   SysRoleMenuMapper sysRoleMenuMapper,
                                   SysMenuMapper sysMenuMapper) {
        this.sysUserService = sysUserService;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysRoleMenuMapper = sysRoleMenuMapper;
        this.sysMenuMapper = sysMenuMapper;
    }

    @Override
    public LoginUser loadByUsername(String username) {
        SysUser user = sysUserService.getByUsername(username);
        if (user == null) {
            return null;
        }
        return toLoginUser(user);
    }

    @Override
    public LoginUser loadByUserId(Long userId) {
        SysUser user = sysUserService.getById(userId);
        if (user == null) {
            return null;
        }
        return toLoginUser(user);
    }

    private LoginUser toLoginUser(SysUser user) {
        // 1. 查询用户关联的角色
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
                Wrappers.lambdaQuery(SysUserRole.class)
                        .eq(SysUserRole::getUserId, user.getId()));

        List<String> roleCodes;
        boolean superAdmin;

        if (userRoles.isEmpty()) {
            roleCodes = Collections.emptyList();
            superAdmin = false;
        } else {
            List<Long> roleIds = userRoles.stream()
                    .map(SysUserRole::getRoleId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // 2. 加载已启用角色的 code
            List<SysRole> roles = sysRoleMapper.selectList(
                    Wrappers.lambdaQuery(SysRole.class)
                            .in(SysRole::getId, roleIds)
                            .eq(SysRole::getStatus, 1));

            roleCodes = roles.stream()
                    .map(SysRole::getCode)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            superAdmin = roleCodes.contains(SUPER_ADMIN_ROLE_CODE);
        }

        // 3. 装配 LoginUser
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setDeptId(user.getDeptId());
        loginUser.setTenantId(user.getTenantId());
        loginUser.setRoles(roleCodes);
        loginUser.setDataScope(DataScope.ALL);

        if (superAdmin) {
            loginUser.setSuperAdmin(true);
            // 超管旁路权限：前端 hasPerm = superAdmin || hasPermission，空数组即可
            loginUser.setPermissions(Collections.emptyList());
        } else {
            loginUser.setSuperAdmin(false);
            loginUser.setPermissions(loadPermissions(user));
        }

        return loginUser;
    }

    /**
     * 加载非超管用户的菜单权限标识。
     * 路径：sys_user_role → sys_role_menu → sys_menu（menu_type=2 按钮，permission 非空）
     */
    private List<String> loadPermissions(SysUser user) {
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
                Wrappers.lambdaQuery(SysUserRole.class)
                        .eq(SysUserRole::getUserId, user.getId()));
        if (userRoles.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> roleIds = userRoles.stream()
                .map(SysUserRole::getRoleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<SysRoleMenu> roleMenus = sysRoleMenuMapper.selectList(
                Wrappers.lambdaQuery(SysRoleMenu.class)
                        .in(SysRoleMenu::getRoleId, roleIds));
        if (roleMenus.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> menuIds = roleMenus.stream()
                .map(SysRoleMenu::getMenuId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 仅取菜单类型=按钮(2)且 permission 非空白的记录
        List<SysMenu> menus = sysMenuMapper.selectList(
                Wrappers.lambdaQuery(SysMenu.class)
                        .in(SysMenu::getId, menuIds)
                        .eq(SysMenu::getMenuType, 2)
                        .isNotNull(SysMenu::getPermission)
                        .ne(SysMenu::getPermission, ""));

        return menus.stream()
                .map(SysMenu::getPermission)
                .distinct()
                .collect(Collectors.toList());
    }
}
