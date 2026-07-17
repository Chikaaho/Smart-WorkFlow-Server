package com.sw.ck.system.controller;

import com.sw.ck.common.page.PageParam;
import com.sw.ck.common.page.PageResult;
import com.sw.ck.common.response.R;
import com.sw.ck.system.entity.SysUser;
import com.sw.ck.system.service.SysUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理控制器。
 */
@RestController
@RequestMapping("/system/user")
public class UserController {

    private final SysUserService sysUserService;

    public UserController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    /** 内嵌 DTO：用户表单（含明文密码） */
    @Data
    public static class UserFormRequest {
        private Long id;
        @NotBlank(message = "用户名不能为空")
        private String username;
        private String realName;
        private String email;
        private String phone;
        private Integer sex;
        private Integer status;
        private Long deptId;
        /** 明文密码 — 新建时必填，更新时为空表示不修改 */
        private String plainPassword;
    }

    /**
     * 分页查询用户。
     */
    @PostMapping("/page")
    public R<PageResult<SysUser>> page(@RequestParam(defaultValue = "1") long pageNum,
                                        @RequestParam(defaultValue = "10") long pageSize,
                                        @RequestBody(required = false) SysUser query) {
        PageParam pageParam = new PageParam();
        pageParam.setPageNum(pageNum);
        pageParam.setPageSize(pageSize);
        // SysUserService.page(PageParam) 仅接受 PageParam（无 query 筛选）
        return R.ok(sysUserService.page(pageParam));
    }

    /**
     * 获取用户详情。
     */
    @GetMapping("/{id}")
    public R<SysUser> get(@PathVariable Long id) {
        return R.ok(sysUserService.getById(id));
    }

    /**
     * 创建用户。
     */
    @PostMapping
    public R<Long> create(@Valid @RequestBody UserFormRequest req) {
        SysUser user = toEntity(req);
        return R.ok(sysUserService.create(user, req.getPlainPassword()));
    }

    /**
     * 更新用户。
     */
    @PutMapping
    public R<Void> update(@Valid @RequestBody UserFormRequest req) {
        SysUser user = toEntity(req);
        sysUserService.update(user, req.getPlainPassword());
        return R.ok();
    }

    /**
     * 删除用户（逻辑删除）。
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        sysUserService.delete(id);
        return R.ok();
    }

    /** UserFormRequest → SysUser 转换 */
    private SysUser toEntity(UserFormRequest req) {
        SysUser user = new SysUser();
        user.setId(req.getId());
        user.setUsername(req.getUsername());
        user.setRealName(req.getRealName());
        user.setEmail(req.getEmail());
        user.setPhone(req.getPhone());
        user.setSex(req.getSex());
        user.setStatus(req.getStatus());
        user.setDeptId(req.getDeptId());
        return user;
    }
}
