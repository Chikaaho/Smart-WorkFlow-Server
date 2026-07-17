package com.sw.ck.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.sw.ck.common.page.PageParam;
import com.sw.ck.common.page.PageResult;
import com.sw.ck.common.service.BaseServiceImpl;
import com.sw.ck.system.entity.SysPost;
import com.sw.ck.system.mapper.SysPostMapper;
import com.sw.ck.system.service.SysPostService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 岗位 Service 实现。
 */
@Service
public class SysPostServiceImpl
        extends BaseServiceImpl<SysPostMapper, SysPost>
        implements SysPostService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SysPost post) {
        save(post);
        return post.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SysPost post) {
        updateById(post);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        removeById(id);
    }

    @Override
    public PageResult<SysPost> page(PageParam pageParam, SysPost query) {
        LambdaQueryWrapper<SysPost> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (StringUtils.isNotBlank(query.getCode())) {
                wrapper.like(SysPost::getCode, query.getCode());
            }
            if (StringUtils.isNotBlank(query.getName())) {
                wrapper.like(SysPost::getName, query.getName());
            }
            if (query.getStatus() != null) {
                wrapper.eq(SysPost::getStatus, query.getStatus());
            }
        }
        wrapper.orderByAsc(SysPost::getCreateTime);
        return baseMapper.selectPage(pageParam, wrapper);
    }
}
