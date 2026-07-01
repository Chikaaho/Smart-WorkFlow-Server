package com.sw.ck.bpm.process.listener;

import com.sw.ck.bpm.process.dto.StartCommand;
import com.sw.ck.bpm.process.service.ProcessStartService;
import com.sw.ck.form.api.event.FormSubmittedEvent;
import com.sw.ck.security.holder.LoginUser;
import com.sw.ck.security.holder.LoginUserHolder;
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
 * <h3>上下文还原</h3>
 * 异步线程中 {@link LoginUserHolder} 不可用（ThreadLocal 不跨线程），
 * 所有上下文信息（formKey、recordId、tenantId、submitter）均从事件 payload 获取。
 * 入口处还原 LoginUserHolder，使 MyBatis-Plus 拦截器自动注入 tenant_id/审计列；
 * finally 中 clear。
 *
 * <h3>单入口</h3>
 * listener 只做：取 payload → 还原上下文 → 拼 StartCommand → 调 ProcessStartService.start(cmd)，
 * 不包含任何业务逻辑。未来 job FLOW 的 ScheduledFlowTriggerEvent listener 也将拼 StartCommand
 * 汇入同一入口。
 */
@Component
public class FormSubmittedEventListener {

    private static final Logger log = LoggerFactory.getLogger(FormSubmittedEventListener.class);

    private final ProcessStartService processStartService;

    public FormSubmittedEventListener(ProcessStartService processStartService) {
        this.processStartService = processStartService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFormSubmitted(FormSubmittedEvent event) {
        // 从事件 payload 还原上下文（异步线程中 LoginUserHolder 为空）
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(Long.valueOf(event.getSubmitter()));
        loginUser.setTenantId(event.getTenantId());
        LoginUserHolder.set(loginUser);
        try {
            // 拼 StartCommand，汇入 ProcessStartService 唯一入口
            StartCommand cmd = new StartCommand();
            cmd.setFormKey(event.getFormKey());
            cmd.setRecordId(event.getRecordId());
            cmd.setSubmitter(Long.valueOf(event.getSubmitter()));
            cmd.setTenantId(event.getTenantId());
            cmd.setSubmittedData(event.getSubmittedData());

            processStartService.start(cmd);
        } catch (Exception e) {
            log.error("流程发起失败: formKey={}, recordId={}", event.getFormKey(), event.getRecordId(), e);
        } finally {
            LoginUserHolder.clear();
        }
    }
}
