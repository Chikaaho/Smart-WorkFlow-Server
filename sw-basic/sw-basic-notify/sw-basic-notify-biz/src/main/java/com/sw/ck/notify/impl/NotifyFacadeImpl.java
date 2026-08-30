package com.sw.ck.notify.impl;

import com.sw.ck.notify.api.NotifyFacade;
import com.sw.ck.notify.api.SendNotifyCommand;
import com.sw.ck.notify.entity.NotifyMessage;
import com.sw.ck.notify.service.NotifyMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 通知门面实现。
 * <p>
 * 将 {@link SendNotifyCommand} 映射为 {@link NotifyMessage} 实体并落库。
 * {@code tenant_id / 审计列 / deleted / version} 由 MyBatis-Plus 拦截器自动注入，
 * 本实现不手填、不读 ThreadLocal、不使用 {@code cmd.tenantId}。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class NotifyFacadeImpl implements NotifyFacade {

    private final NotifyMessageService notifyMessageService;

    @Override
    public void send(SendNotifyCommand cmd) {
        NotifyMessage msg = new NotifyMessage();
        msg.setRecipientId(cmd.getRecipientId());
        msg.setTitle(cmd.getTitle());
        msg.setContent(cmd.getContent());
        msg.setBizType(cmd.getBizType().name());
        msg.setBizId(cmd.getBizId());
        msg.setRead(false);
        // tenant_id / 审计列 / deleted / version 由拦截器自动注入，不手填
        notifyMessageService.save(msg);
    }
}
