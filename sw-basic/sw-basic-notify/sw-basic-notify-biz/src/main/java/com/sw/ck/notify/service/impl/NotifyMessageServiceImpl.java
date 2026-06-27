package com.sw.ck.notify.service.impl;

import com.sw.ck.common.service.BaseServiceImpl;
import com.sw.ck.notify.entity.NotifyMessage;
import com.sw.ck.notify.mapper.NotifyMessageMapper;
import com.sw.ck.notify.service.NotifyMessageService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 站内信通知 Service 实现。
 */
@Service
public class NotifyMessageServiceImpl
        extends BaseServiceImpl<NotifyMessageMapper, NotifyMessage>
        implements NotifyMessageService {

    @Override
    public List<NotifyMessage> findByRecipient(Long recipientId) {
        return lambdaQuery()
                .eq(NotifyMessage::getRecipientId, recipientId)
                .orderByDesc(NotifyMessage::getCreateTime)
                .list();
    }
}
