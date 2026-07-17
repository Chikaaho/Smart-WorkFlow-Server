package com.sw.ck.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.sw.ck.common.exception.BaseException;
import com.sw.ck.common.exception.CommonErrorCode;
import com.sw.ck.common.page.PageParam;
import com.sw.ck.common.page.PageResult;
import com.sw.ck.common.service.BaseServiceImpl;
import com.sw.ck.system.entity.SysRole;
import com.sw.ck.system.mapper.SysRoleMapper;
import com.sw.ck.system.service.SysRoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统角色 Service 实现。
 */
@Service
public class SysRoleServiceImpl
        extends BaseServiceImpl<SysRoleMapper, SysRole>
        implements SysRoleService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SysRole role) {
        // 校验编码唯一性
        if (getByCode(role.getCode()) != null) {
            throw new BaseException(CommonErrorCode.PARAM_ERROR, "角色编码已存在");
        }
        save(role);
        return role.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SysRole role) {
        // 校验编码唯一性（排除自身）
        SysRole existing = getByCode(role.getCode());
        if (existing != null && !existing.getId().equals(role.getId())) {
            throw new BaseException(CommonErrorCode.PARAM_ERROR, "角色编码已存在");
        }
        updateById(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        removeById(id);
    }

    @Override
    public PageResult<SysRole> page(PageParam pageParam, SysRole query) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (StringUtils.isNotBlank(query.getName())) {
                wrapper.like(SysRole::getName, query.getName());
            }
            if (StringUtils.isNotBlank(query.getCode())) {
                wrapper.like(SysRole::getCode, query.getCode());
            }
            if (query.getStatus() != null) {
                wrapper.eq(SysRole::getStatus, query.getStatus());
            }
        }
        wrapper.orderByAsc(SysRole::getCreateTime);
        return baseMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public SysRole getByCode(String code) {
        return lambdaQuery().eq(SysRole::getCode, code).one();
    }
}
