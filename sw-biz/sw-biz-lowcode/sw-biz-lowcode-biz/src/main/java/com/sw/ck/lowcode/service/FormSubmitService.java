package com.sw.ck.lowcode.service;

import com.sw.ck.lowcode.api.event.FormSubmittedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 表单提交流程骨架。
 * <p>
 * 提交时 publish FormSubmittedEvent，由 workflow 模块监听决定是否发起流程。
 * 本类为骨架占位，后续接入实际表单数据后完善。
 */
@Service
public class FormSubmitService {

    private static final Logger log = LoggerFactory.getLogger(FormSubmitService.class);

    private final ApplicationEventPublisher eventPublisher;

    public FormSubmitService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * 提交表单数据并发布 FormSubmittedEvent。
     *
     * @param formKey       表单标识
     * @param submittedData 提交数据
     * @param submitter     提交人
     */
    public void submitForm(String formKey, Map<String, Object> submittedData, String submitter) {
        log.info("表单提交: formKey={}, submitter={}", formKey, submitter);
        // TODO: 存储表单提交数据

        // 发布事件，触发工作流
        FormSubmittedEvent event = new FormSubmittedEvent(formKey, submittedData, submitter);
        eventPublisher.publishEvent(event);
    }
}
