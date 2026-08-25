package com.sw.ck.notify.service.impl;

import com.sw.ck.common.service.BaseServiceImpl;
import com.sw.ck.notify.entity.NotifyMessage;
import com.sw.ck.notify.mapper.NotifyMessageMapper;
import com.sw.ck.notify.service.NotifyMessageService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

    @Override
    public List<NotifyMessage> findByRecipientWithFilter(Long recipientId, Boolean read, String keyword) {
        var wrapper = lambdaQuery()
                .eq(NotifyMessage::getRecipientId, recipientId);
        if (read != null) {
            wrapper.eq(NotifyMessage::getRead, read);
        }
        if (StringUtils.hasText(keyword)) {
            String pattern = "%" + keyword + "%";
            wrapper.and(w -> w
                    .like(NotifyMessage::getTitle, pattern)
                    .or()
                    .like(NotifyMessage::getContent, pattern));
        }
        return wrapper.orderByDesc(NotifyMessage::getCreateTime).list();
    }

    @Override
    public void deleteMessage(Long id) {
        removeById(id);
    }
}
