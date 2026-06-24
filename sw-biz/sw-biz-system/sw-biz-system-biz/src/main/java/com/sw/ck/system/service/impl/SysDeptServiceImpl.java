package com.sw.ck.system.service.impl;

import com.sw.ck.common.exception.BaseException;
import com.sw.ck.common.exception.CommonErrorCode;
import com.sw.ck.common.service.BaseServiceImpl;
import com.sw.ck.system.entity.SysDept;
import com.sw.ck.system.entity.SysUser;
import com.sw.ck.system.mapper.SysDeptMapper;
import com.sw.ck.system.service.SysDeptService;
import com.sw.ck.system.service.SysUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 部门 Service 实现。
 */
@Service
public class SysDeptServiceImpl
        extends BaseServiceImpl<SysDeptMapper, SysDept>
        implements SysDeptService {

    private final SysUserService sysUserService;

    public SysDeptServiceImpl(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SysDept dept) {
        save(dept);
        return dept.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SysDept dept) {
        updateById(dept);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 校验：是否有子部门
        Long childCount = lambdaQuery().eq(SysDept::getParentId, id).count();
        if (childCount != null && childCount > 0) {
            throw new BaseException(CommonErrorCode.PARAM_ERROR, "该部门存在子部门，无法删除");
        }
        // 校验：是否有在职用户
        Long userCount = sysUserService.lambdaQuery()
                .eq(SysUser::getDeptId, id)
                .eq(SysUser::getStatus, 0)
                .count();
        if (userCount != null && userCount > 0) {
            throw new BaseException(CommonErrorCode.PARAM_ERROR, "该部门下存在在职用户，无法删除");
        }
        removeById(id);
    }

    @Override
    public List<SysDept> listTree() {
        return lambdaQuery()
                .orderByAsc(SysDept::getSort)
                .list();
    }
}
