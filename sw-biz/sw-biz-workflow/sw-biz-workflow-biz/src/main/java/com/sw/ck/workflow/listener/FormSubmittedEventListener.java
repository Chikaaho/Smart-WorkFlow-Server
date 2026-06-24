package com.sw.ck.workflow.listener;

import com.sw.ck.form.api.event.FormSubmittedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 监听表单提交事件，按需发起流程。
 * <p>
 * 使用 {@code @TransactionalEventListener(AFTER_COMMIT)} 确保只在事务提交后触发
 *（事务回滚时不触发），配合 {@code @Async} 异步执行，不阻塞提交线程。
 * </p>
 *
 * <h3>线程安全</h3>
 * 异步线程中 {@link com.sw.ck.security.holder.LoginUserHolder} 不可用（ThreadLocal 不跨线程），
 * 所有上下文信息（formKey、recordId、tenantId、submitter）均从事件 payload 获取。
 */
@Component
public class FormSubmittedEventListener {

    private static final Logger log = LoggerFactory.getLogger(FormSubmittedEventListener.class);

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFormSubmitted(FormSubmittedEvent event) {
        log.info("收到表单提交事件: formKey={}, recordId={}, submitter={}, tenantId={}",
                event.getFormKey(), event.getRecordId(), event.getSubmitter(), event.getTenantId());
        // TODO: 根据 formKey 查询绑定的流程定义，发起流程实例
    }
}
