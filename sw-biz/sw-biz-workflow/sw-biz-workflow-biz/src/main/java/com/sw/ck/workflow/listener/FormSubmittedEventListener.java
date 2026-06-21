package com.sw.ck.workflow.listener;

import com.sw.ck.lowcode.api.event.FormSubmittedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 监听表单提交事件，按需发起流程。
 * <p>
 * 通过 @EventListener 消费 FormSubmittedEvent，做到 lowcode → workflow 的事件驱动解耦。
 */
@Component
public class FormSubmittedEventListener {

    private static final Logger log = LoggerFactory.getLogger(FormSubmittedEventListener.class);

    @EventListener
    public void onFormSubmitted(FormSubmittedEvent event) {
        log.info("收到表单提交事件: formKey={}, submitter={}", event.getFormKey(), event.getSubmitter());
        // TODO: 根据 formKey 查询绑定的流程定义，发起流程实例
    }
}
