package com.sw.ck.common.service;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * 业务 ServiceImpl 统一基类。业务侧：{@code XxxServiceImpl extends BaseServiceImpl<XxxMapper, Xxx>}，
 * 简单 CRUD 直接用 lambdaQuery()/lambdaUpdate() 链式调用，不手写 SQL；复杂查询才落 XML/注解。
 */
public class BaseServiceImpl<M extends BaseMapper<T>, T> extends ServiceImpl<M, T> implements BaseService<T> {
}
