package com.sw.ck.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.sw.ck.common.page.PageParam;
import com.sw.ck.common.page.PageResult;
import com.sw.ck.common.service.BaseServiceImpl;
import com.sw.ck.system.entity.SysDictData;
import com.sw.ck.system.mapper.SysDictDataMapper;
import com.sw.ck.system.service.SysDictDataService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 字典数据 Service 实现。
 */
@Service
public class SysDictDataServiceImpl
        extends BaseServiceImpl<SysDictDataMapper, SysDictData>
        implements SysDictDataService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SysDictData dictData) {
        save(dictData);
        return dictData.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SysDictData dictData) {
        updateById(dictData);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        removeById(id);
    }

    @Override
    public PageResult<SysDictData> page(PageParam pageParam, SysDictData query) {
        LambdaQueryWrapper<SysDictData> wrapper = buildQueryWrapper(query);
        wrapper.orderByAsc(SysDictData::getSort);
        return baseMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public List<SysDictData> listByDictCode(String dictCode) {
        return lambdaQuery()
                .eq(SysDictData::getDictCode, dictCode)
                .eq(SysDictData::getStatus, 0)
                .orderByAsc(SysDictData::getSort)
                .list();
    }

    @Override
    public boolean isValidCode(String dictCode, String value) {
        return lambdaQuery()
                .eq(SysDictData::getDictCode, dictCode)
                .eq(SysDictData::getDictValue, value)
                .eq(SysDictData::getStatus, 0)
                .exists();
    }

    @Override
    public String resolveLabel(String dictCode, String value) {
        SysDictData item = lambdaQuery()
                .eq(SysDictData::getDictCode, dictCode)
                .eq(SysDictData::getDictValue, value)
                .eq(SysDictData::getStatus, 0)
                .one();
        return item != null ? item.getLabel() : null;
    }

    private LambdaQueryWrapper<SysDictData> buildQueryWrapper(SysDictData query) {
        LambdaQueryWrapper<SysDictData> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (StringUtils.isNotBlank(query.getDictCode())) {
                wrapper.eq(SysDictData::getDictCode, query.getDictCode());
            }
            if (StringUtils.isNotBlank(query.getLabel())) {
                wrapper.like(SysDictData::getLabel, query.getLabel());
            }
            if (StringUtils.isNotBlank(query.getDictValue())) {
                wrapper.like(SysDictData::getDictValue, query.getDictValue());
            }
            if (query.getStatus() != null) {
                wrapper.eq(SysDictData::getStatus, query.getStatus());
            }
        }
        return wrapper;
    }
}
