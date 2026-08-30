package com.sw.ck.common.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sw.ck.common.page.PageParam;
import com.sw.ck.common.page.PageResult;

/**
 * 业务 Mapper 统一基类。业务侧：{@code XxxMapper extends BaseMapperX<Xxx>}。
 */
public interface BaseMapperX<T> extends BaseMapper<T> {

    default PageResult<T> selectPage(PageParam pageParam, Wrapper<T> queryWrapper) {
        Page<T> mpPage = selectPage(new Page<>(pageParam.getPageNum(), pageParam.getPageSize()), queryWrapper);
        return PageResult.of(mpPage);
    }
}
