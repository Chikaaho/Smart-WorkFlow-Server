package com.sw.ck.notify.service;

import com.sw.ck.common.service.BaseService;
import com.sw.ck.notify.entity.NotifyMessage;

import java.util.List;

/**
 * 站内信通知 Service。
 * <p>
 * 查询时租户条件由 {@code TenantLineHandler} 自动注入，
 * 调用方只需手写 {@code recipient_id} 条件。
 * </p>
 */
public interface NotifyMessageService extends BaseService<NotifyMessage> {

    /**
     * 按接收人查询通知列表。
     * <p>
     * 租户条件由拦截器自动追加，本方法只写 recipient_id 条件。
     * </p>
     *
     * @param recipientId 接收人用户 ID
     * @return 通知列表
     */
    List<NotifyMessage> findByRecipient(Long recipientId);
}
