package com.sw.ck.system.security;

import com.sw.ck.security.holder.DataScope;
import com.sw.ck.security.holder.LoginUser;
import com.sw.ck.security.spi.UserDetailsProvider;
import com.sw.ck.system.entity.SysUser;
import com.sw.ck.system.service.SysUserService;

import java.util.Collections;

/**
 * sw-security SPI 实现：从组织架构 SysUser 表加载用户认证信息。
 * <p>
 * 本切片 roles / permissions 先返回空集合；userId == 1 视为 superAdmin。
 * </p>
 */
public class UserDetailsProviderImpl implements UserDetailsProvider {

    private final SysUserService sysUserService;

    public UserDetailsProviderImpl(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
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
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setDeptId(user.getDeptId());
        // 本切片 roles/permissions 先返回空集合
        loginUser.setRoles(Collections.emptyList());
        loginUser.setPermissions(Collections.emptyList());
        loginUser.setDataScope(DataScope.ALL);
        // userId == 1 视为超管
        loginUser.setSuperAdmin(user.getId() != null && user.getId() == 1L);
        // tenantId 使用用户记录的 tenantId
        loginUser.setTenantId(user.getTenantId());
        return loginUser;
    }
}
