package com.sw.ck.system.controller;

import com.sw.ck.common.page.PageParam;
import com.sw.ck.common.page.PageResult;
import com.sw.ck.common.response.R;
import com.sw.ck.system.entity.SysRole;
import com.sw.ck.system.service.SysRoleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 角色管理控制器。
 */
@RestController
@RequestMapping("/system/role")
public class RoleController {

    private final SysRoleService sysRoleService;

    public RoleController(SysRoleService sysRoleService) {
        this.sysRoleService = sysRoleService;
    }

    /**
     * 分页查询角色。
     */
    @PostMapping("/page")
    public R<PageResult<SysRole>> page(@RequestParam(defaultValue = "1") long pageNum,
                                        @RequestParam(defaultValue = "10") long pageSize,
                                        @RequestBody(required = false) SysRole query) {
        PageParam pageParam = new PageParam();
        pageParam.setPageNum(pageNum);
        pageParam.setPageSize(pageSize);
        return R.ok(sysRoleService.page(pageParam, query));
    }

    /**
     * 获取角色详情。
     */
    @GetMapping("/{id}")
    public R<SysRole> get(@PathVariable Long id) {
        return R.ok(sysRoleService.getById(id));
    }

    /**
     * 创建角色。
     */
    @PostMapping
    public R<Long> create(@Valid @RequestBody SysRole role) {
        return R.ok(sysRoleService.create(role));
    }

    /**
     * 更新角色。
     */
    @PutMapping
    public R<Void> update(@Valid @RequestBody SysRole role) {
        sysRoleService.update(role);
        return R.ok();
    }

    /**
     * 删除角色（逻辑删除）。
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        sysRoleService.delete(id);
        return R.ok();
    }
}
