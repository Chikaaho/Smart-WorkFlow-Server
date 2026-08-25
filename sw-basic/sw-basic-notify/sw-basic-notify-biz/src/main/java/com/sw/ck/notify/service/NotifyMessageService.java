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
     * 按接收人查询通知列表（无过滤）。
     * <p>
     * 租户条件由拦截器自动追加，本方法只写 recipient_id 条件。
     * </p>
     *
     * @param recipientId 接收人用户 ID
     * @return 通知列表
     */
    List<NotifyMessage> findByRecipient(Long recipientId);

    /**
     * 按接收人查询通知列表，支持已读状态和关键词过滤。
     * <p>
     * 租户条件由拦截器自动追加，本方法只写 recipient_id 条件。
     * </p>
     *
     * @param recipientId 接收人用户 ID
     * @param read        已读状态过滤（null = 不过滤）
     * @param keyword     关键词过滤，匹配标题或内容（null/空 = 不过滤）
     * @return 通知列表
     */
    List<NotifyMessage> findByRecipientWithFilter(Long recipientId, Boolean read, String keyword);

    /**
     * 删除指定接收人的通知（逻辑删除，由 MyBatis-Plus @TableLogic 生效）。
     * <p>
     * 租户条件由拦截器自动追加；调用方须自行校验 recipient 归属。
     * </p>
     *
     * @param id 通知 ID
     */
    void deleteMessage(Long id);
}
