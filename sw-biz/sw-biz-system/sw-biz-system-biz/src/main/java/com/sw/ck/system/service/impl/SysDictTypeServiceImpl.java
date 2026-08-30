package com.sw.ck.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.sw.ck.common.exception.BaseException;
import com.sw.ck.common.exception.CommonErrorCode;
import com.sw.ck.common.page.PageParam;
import com.sw.ck.common.page.PageResult;
import com.sw.ck.common.service.BaseServiceImpl;
import com.sw.ck.system.entity.SysDictType;
import com.sw.ck.system.mapper.SysDictTypeMapper;
import com.sw.ck.system.service.SysDictTypeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 字典类型 Service 实现。
 */
@Service
public class SysDictTypeServiceImpl
        extends BaseServiceImpl<SysDictTypeMapper, SysDictType>
        implements SysDictTypeService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SysDictType dictType) {
        // 校验编码唯一性
        if (getByCode(dictType.getCode()) != null) {
            throw new BaseException(CommonErrorCode.PARAM_ERROR, "字典类型编码已存在");
        }
        save(dictType);
        return dictType.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SysDictType dictType) {
        // 校验编码唯一性（排除自身）
        SysDictType existing = getByCode(dictType.getCode());
        if (existing != null && !existing.getId().equals(dictType.getId())) {
            throw new BaseException(CommonErrorCode.PARAM_ERROR, "字典类型编码已存在");
        }
        updateById(dictType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        removeById(id);
    }

    @Override
    public PageResult<SysDictType> page(PageParam pageParam, SysDictType query) {
        LambdaQueryWrapper<SysDictType> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (StringUtils.isNotBlank(query.getName())) {
                wrapper.like(SysDictType::getName, query.getName());
            }
            if (StringUtils.isNotBlank(query.getCode())) {
                wrapper.like(SysDictType::getCode, query.getCode());
            }
            if (query.getStatus() != null) {
                wrapper.eq(SysDictType::getStatus, query.getStatus());
            }
        }
        wrapper.orderByAsc(SysDictType::getCreateTime);
        return baseMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public SysDictType getByCode(String code) {
        return lambdaQuery().eq(SysDictType::getCode, code).one();
    }
}
