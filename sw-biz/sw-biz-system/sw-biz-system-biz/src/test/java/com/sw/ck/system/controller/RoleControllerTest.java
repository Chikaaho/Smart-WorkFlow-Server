package com.sw.ck.system.controller;

import com.sw.ck.common.page.PageParam;
import com.sw.ck.common.page.PageResult;
import com.sw.ck.common.response.R;
import com.sw.ck.system.entity.SysRole;
import com.sw.ck.system.service.SysRoleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link RoleController} 单元测试。
 */
@DisplayName("角色管理控制器测试")
class RoleControllerTest {

    private final SysRoleService sysRoleService = mock(SysRoleService.class);
    private final RoleController controller = new RoleController(sysRoleService);

    @Test
    @DisplayName("分页查询 → 返回 PageResult")
    void page_shouldReturnPageResult() {
        SysRole role = new SysRole();
        role.setId(1L);
        role.setName("管理员");
        role.setCode("admin");
        role.setDataScope(4);
        role.setDeptIds(List.of(10L, 20L));

        PageResult<SysRole> mockPage = new PageResult<>();
        mockPage.setRecords(List.of(role));
        mockPage.setTotal(1L);
        mockPage.setPageNum(1L);
        mockPage.setPageSize(10L);

        when(sysRoleService.page(any(PageParam.class), any())).thenReturn(mockPage);

        SysRole query = new SysRole();
        query.setName("管理员");

        R<PageResult<SysRole>> result = controller.page(1, 10, query);

        assertThat(result.getCode()).isZero();
        assertThat(result.getData().getRecords()).hasSize(1);
        assertThat(result.getData().getRecords().get(0).getCode()).isEqualTo("admin");
        assertThat(result.getData().getRecords().get(0).getDataScope()).isEqualTo(4);
        assertThat(result.getData().getRecords().get(0).getDeptIds()).containsExactlyInAnyOrder(10L, 20L);
        verify(sysRoleService).page(any(PageParam.class), eq(query));
    }

    @Test
    @DisplayName("分页 query=null → 不抛异常")
    void page_withNullQuery_shouldNotThrow() {
        when(sysRoleService.page(any(PageParam.class), eq(null))).thenReturn(new PageResult<>());

        R<PageResult<SysRole>> result = controller.page(1, 10, null);

        assertThat(result.getCode()).isZero();
    }

    @Test
    @DisplayName("GET /{id} → 返回角色详情（含 dataScope/deptIds）")
    void get_shouldReturnRole() {
        SysRole role = new SysRole();
        role.setId(1L);
        role.setName("管理员");
        role.setCode("admin");
        role.setDataScope(4);
        role.setDeptIds(List.of(10L, 20L));

        when(sysRoleService.getById(1L)).thenReturn(role);

        R<SysRole> result = controller.get(1L);

        assertThat(result.getCode()).isZero();
        assertThat(result.getData().getId()).isEqualTo(1L);
        assertThat(result.getData().getCode()).isEqualTo("admin");
        assertThat(result.getData().getDataScope()).isEqualTo(4);
        assertThat(result.getData().getDeptIds()).containsExactlyInAnyOrder(10L, 20L);
    }

    @Test
    @DisplayName("创建角色 → 返回新 ID（透传 dataScope/deptIds）")
    void create_shouldReturnId() {
        SysRole role = new SysRole();
        role.setName("测试角色");
        role.setCode("test");
        role.setSort(10);
        role.setStatus(1);
        role.setDataScope(4);
        role.setDeptIds(List.of(10L, 20L));

        when(sysRoleService.create(role)).thenReturn(100L);

        R<Long> result = controller.create(role);

        assertThat(result.getCode()).isZero();
        assertThat(result.getData()).isEqualTo(100L);
        verify(sysRoleService).create(role);
        assertThat(role.getDataScope()).isEqualTo(4);
        assertThat(role.getDeptIds()).containsExactlyInAnyOrder(10L, 20L);
    }

    @Test
    @DisplayName("更新角色 → 返回 R.ok()（透传 dataScope/deptIds）")
    void update_shouldReturnOk() {
        SysRole role = new SysRole();
        role.setId(1L);
        role.setName("管理员改名");
        role.setCode("admin");
        role.setDataScope(2);
        role.setDeptIds(List.of(30L));

        doNothing().when(sysRoleService).update(role);

        R<Void> result = controller.update(role);

        assertThat(result.getCode()).isZero();
        verify(sysRoleService).update(role);
        assertThat(role.getDataScope()).isEqualTo(2);
        assertThat(role.getDeptIds()).containsExactlyInAnyOrder(30L);
    }

    @Test
    @DisplayName("DELETE /{id} → 返回 R.ok()")
    void delete_shouldReturnOk() {
        doNothing().when(sysRoleService).delete(1L);

        R<Void> result = controller.delete(1L);

        assertThat(result.getCode()).isZero();
        verify(sysRoleService).delete(1L);
    }
}
