package com.sw.ck.common.service;

import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 业务 Service 统一基类：封装 MyBatis-Plus {@link IService}（lambdaQuery/lambdaUpdate 链式 API + 批量 CRUD）。
 * 业务侧：{@code XxxService extends BaseService<Xxx>}，落在各模块的 -biz（不放 -api，-api 不含 DB 依赖）。
 */
public interface BaseService<T> extends IService<T> {
}
