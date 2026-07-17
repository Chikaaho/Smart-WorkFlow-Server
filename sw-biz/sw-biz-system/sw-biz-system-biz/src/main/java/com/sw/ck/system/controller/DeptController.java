package com.sw.ck.system.controller;

import com.sw.ck.common.response.R;
import com.sw.ck.system.entity.SysDept;
import com.sw.ck.system.service.SysDeptService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 部门管理控制器。
 */
@RestController
@RequestMapping("/system/dept")
public class DeptController {

    private final SysDeptService sysDeptService;

    public DeptController(SysDeptService sysDeptService) {
        this.sysDeptService = sysDeptService;
    }

    /**
     * 查询部门树（返回全量排序列表，前端自行转换为树形结构）。
     */
    @GetMapping("/tree")
    public R<List<SysDept>> tree() {
        return R.ok(sysDeptService.listTree());
    }

    /**
     * 获取部门详情。
     */
    @GetMapping("/{id}")
    public R<SysDept> get(@PathVariable Long id) {
        return R.ok(sysDeptService.getById(id));
    }

    /**
     * 创建部门。
     */
    @PostMapping
    public R<Long> create(@Valid @RequestBody SysDept dept) {
        return R.ok(sysDeptService.create(dept));
    }

    /**
     * 更新部门。
     */
    @PutMapping
    public R<Void> update(@Valid @RequestBody SysDept dept) {
        sysDeptService.update(dept);
        return R.ok();
    }

    /**
     * 删除部门（逻辑删除，含子部门/在职用户校验）。
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        sysDeptService.delete(id);
        return R.ok();
    }
}
