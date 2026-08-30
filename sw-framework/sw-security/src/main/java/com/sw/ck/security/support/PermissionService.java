package com.sw.ck.security.support;

import com.sw.ck.security.holder.LoginUser;
import com.sw.ck.security.holder.LoginUserHolder;

/**
 * 方法级鉴权表达式入口，注册为名为 {@code "ss"} 的 Bean，供
 * {@code @PreAuthorize("@ss.hasPermi('system:user:list')")} 使用。
 * <p>
 * 权限判断直接基于 {@link LoginUserHolder} 中的 {@link LoginUser#getPermissions()}/
 * {@link LoginUser#getRoles()}，不依赖 Spring Security 的 GrantedAuthority 体系；
 * {@link LoginUser#isSuperAdmin()} 为 true 时一律短路放行。
 */
public class PermissionService {

    public boolean hasPermi(String permission) {
        LoginUser loginUser = LoginUserHolder.get();
        if (loginUser == null) {
            return false;
        }
        if (loginUser.isSuperAdmin()) {
            return true;
        }
        return loginUser.getPermissions() != null && loginUser.getPermissions().contains(permission);
    }

    public boolean hasAnyPermi(String... permissions) {
        LoginUser loginUser = LoginUserHolder.get();
        if (loginUser == null) {
            return false;
        }
        if (loginUser.isSuperAdmin()) {
            return true;
        }
        if (loginUser.getPermissions() == null) {
            return false;
        }
        for (String permission : permissions) {
            if (loginUser.getPermissions().contains(permission)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasRole(String role) {
        LoginUser loginUser = LoginUserHolder.get();
        if (loginUser == null) {
            return false;
        }
        if (loginUser.isSuperAdmin()) {
            return true;
        }
        return loginUser.getRoles() != null && loginUser.getRoles().contains(role);
    }
}
