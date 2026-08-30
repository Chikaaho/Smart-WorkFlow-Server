package com.sw.ck.security.support;

import com.sw.ck.security.holder.LoginUser;
import com.sw.ck.security.holder.LoginUserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionServiceTest {

    private final PermissionService permissionService = new PermissionService();

    @AfterEach
    void clearLoginContext() {
        LoginUserHolder.clear();
    }

    @Test
    void ordinaryAdmin_explicitPermissionAllowsAndRevocationDenies() {
        LoginUser admin = new LoginUser();
        admin.setRoles(List.of("admin"));
        admin.setPermissions(List.of("job:list", "job:create", "storage:list"));
        LoginUserHolder.set(admin);

        assertThat(permissionService.hasPermi("job:create")).isTrue();
        assertThat(permissionService.hasPermi("job:delete")).isFalse();

        admin.setPermissions(List.of("job:list"));
        assertThat(permissionService.hasPermi("job:create")).isFalse();
    }

    @Test
    void superadminBypassAllowsWithoutPermissionRecords() {
        LoginUser superadmin = new LoginUser();
        superadmin.setRoles(List.of("superadmin"));
        superadmin.setSuperAdmin(true);
        superadmin.setPermissions(List.of());
        LoginUserHolder.set(superadmin);

        assertThat(permissionService.hasPermi("storage:download")).isTrue();
        assertThat(permissionService.hasRole("any-role")).isTrue();
    }
}
