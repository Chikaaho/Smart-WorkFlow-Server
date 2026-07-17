package com.sw.ck.system.controller;

import com.sw.ck.common.response.R;
import com.sw.ck.system.entity.SysDept;
import com.sw.ck.system.service.SysDeptService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * {@link DeptController} 单元测试。
 * <p>
 * 特殊：部门无分页端点，用 GET /tree 返回全量排序列表。
 * </p>
 */
@DisplayName("部门管理控制器测试")
class DeptControllerTest {

    private final SysDeptService sysDeptService = mock(SysDeptService.class);
    private final DeptController controller = new DeptController(sysDeptService);

    @Test
    @DisplayName("GET /tree → 返回部门列表")
    void tree_shouldReturnList() {
        SysDept dept1 = new SysDept();
        dept1.setId(1L);
        dept1.setName("总公司");
        dept1.setCode("root");

        SysDept dept2 = new SysDept();
        dept2.setId(2L);
        dept2.setName("研发部");
        dept2.setCode("dev");
        dept2.setParentId(1L);

        when(sysDeptService.listTree()).thenReturn(List.of(dept1, dept2));

        R<List<SysDept>> result = controller.tree();

        assertThat(result.getCode()).as("成功码应为 0").isZero();
        assertThat(result.getData()).hasSize(2);
        assertThat(result.getData().get(0).getName()).isEqualTo("总公司");
        assertThat(result.getData().get(1).getParentId()).isEqualTo(1L);
        verify(sysDeptService).listTree();
    }

    @Test
    @DisplayName("GET /tree 空列表 → 返回空数组不抛异常")
    void tree_empty_shouldReturnEmptyList() {
        when(sysDeptService.listTree()).thenReturn(Collections.emptyList());

        R<List<SysDept>> result = controller.tree();

        assertThat(result.getCode()).isZero();
        assertThat(result.getData()).isEmpty();
    }

    @Test
    @DisplayName("GET /{id} → 返回部门详情")
    void get_shouldReturnDept() {
        SysDept dept = new SysDept();
        dept.setId(1L);
        dept.setName("总公司");
        dept.setCode("root");
        dept.setParentId(0L);

        when(sysDeptService.getById(1L)).thenReturn(dept);

        R<SysDept> result = controller.get(1L);

        assertThat(result.getCode()).isZero();
        assertThat(result.getData().getName()).isEqualTo("总公司");
    }

    @Test
    @DisplayName("创建部门 → 返回新 ID")
    void create_shouldReturnId() {
        SysDept dept = new SysDept();
        dept.setName("新部门");
        dept.setCode("new_dept");
        dept.setParentId(1L);
        dept.setSort(10);

        when(sysDeptService.create(dept)).thenReturn(200L);

        R<Long> result = controller.create(dept);

        assertThat(result.getCode()).isZero();
        assertThat(result.getData()).isEqualTo(200L);
        verify(sysDeptService).create(dept);
    }

    @Test
    @DisplayName("更新部门 → 返回 R.ok()")
    void update_shouldReturnOk() {
        SysDept dept = new SysDept();
        dept.setId(1L);
        dept.setName("总公司改名");

        doNothing().when(sysDeptService).update(dept);

        R<Void> result = controller.update(dept);

        assertThat(result.getCode()).isZero();
        verify(sysDeptService).update(dept);
    }

    @Test
    @DisplayName("DELETE /{id} → 返回 R.ok()")
    void delete_shouldReturnOk() {
        doNothing().when(sysDeptService).delete(1L);

        R<Void> result = controller.delete(1L);

        assertThat(result.getCode()).isZero();
        verify(sysDeptService).delete(1L);
    }
}
