package com.sw.ck.bpm.process.listener;

import com.sw.ck.bpm.api.event.BpmDeviceCommandEvent;
import com.sw.ck.iot.api.IotDeviceFacade;
import com.sw.ck.iot.entity.IotDeviceCommand;
import com.sw.ck.iot.mapper.IotDeviceCommandMapper;
import com.sw.ck.security.holder.LoginUser;
import com.sw.ck.security.holder.LoginUserHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 审批通过后下发设备命令（审批结果驱动设备）。
 * <p>
 * 监听 {@link BpmDeviceCommandEvent}（AFTER_COMMIT + 异步），经
 * {@link IotDeviceFacade} 下发命令，approvalBizId = 流程实例 ID，
 * 命令执行结果可在 {@code /iot/devices/{productId}/{deviceName}/commands} 回查。
 * </p>
 *
 * <h3>容错</h3>
 * IoT 模块未装配时（ObjectProvider 为空）跳过；设备下发失败时保存命令失败状态
 * （status=FAILED, last_error=脱敏错误信息），审批事务不受影响。
 */
@Component
public class BpmDeviceCommandListener {

    private static final Logger log = LoggerFactory.getLogger(BpmDeviceCommandListener.class);

    private final ObjectProvider<IotDeviceFacade> iotDeviceFacadeProvider;
    private final ObjectProvider<IotDeviceCommandMapper> commandMapperProvider;

    public BpmDeviceCommandListener(ObjectProvider<IotDeviceFacade> iotDeviceFacadeProvider,
                                    ObjectProvider<IotDeviceCommandMapper> commandMapperProvider) {
        this.iotDeviceFacadeProvider = iotDeviceFacadeProvider;
        this.commandMapperProvider = commandMapperProvider;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProcessApproved(BpmDeviceCommandEvent event) {
        IotDeviceFacade facade = iotDeviceFacadeProvider.getIfAvailable();
        if (facade == null) {
            log.warn("IoT 设备门面未装配，跳过设备命令下发: processInstanceId={}, productId={}, deviceName={}",
                    event.getProcessInstanceId(), event.getProductId(), event.getDeviceName());
            return;
        }
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(event.getActorUserId());
        loginUser.setTenantId(event.getTenantId());
        LoginUserHolder.set(loginUser);
        try {
            Long commandId = facade.dispatchCommand(
                    event.getProductId(), event.getDeviceName(),
                    event.getCommandKey(), event.getCommandType(),
                    null, event.getProcessInstanceId());
            log.info("审批联动设备命令已下发: commandId={}, processInstanceId={}, productId={}, deviceName={}, commandKey={}",
                    commandId, event.getProcessInstanceId(), event.getProductId(), event.getDeviceName(), event.getCommandKey());
        } catch (Exception e) {
            log.error("审批联动设备命令下发失败: processInstanceId={}, productId={}, deviceName={}, commandKey={}",
                    event.getProcessInstanceId(), event.getProductId(), event.getDeviceName(), event.getCommandKey(), e);
            // 保存命令失败状态，确保审批完成但命令失败可查询
            saveCommandFailure(event, e);
        } finally {
            LoginUserHolder.clear();
        }
    }

    /**
     * 设备命令下发失败时，保存失败状态到命令记录。
     * <p>
     * 审批事务已提交不可回滚；此处将命令标记为 FAILED 并保存脱敏错误信息，
     * 确保审批成功 + 命令失败可同时查询。
     * </p>
     */
    private void saveCommandFailure(BpmDeviceCommandEvent event, Exception e) {
        try {
            IotDeviceCommandMapper commandMapper = commandMapperProvider.getIfAvailable();
            if (commandMapper == null) {
                return;
            }
            // 创建失败命令记录，关联审批 ID
            IotDeviceCommand command = new IotDeviceCommand();
            command.setProductId(event.getProductId());
            command.setDeviceName(event.getDeviceName());
            command.setCommandType(event.getCommandType() != null ? event.getCommandType() : "PROPERTY");
            command.setCommandKey(event.getCommandKey());
            command.setSemanticMode("DEFERRED");
            command.setStatus("FAILED");
            command.setApprovalBizId(event.getProcessInstanceId());
            command.setLastError(desensitizeError(e));
            commandMapper.insert(command);
            log.info("设备命令失败状态已保存: productId={}, deviceName={}, approvalBizId={}",
                    event.getProductId(), event.getDeviceName(), event.getProcessInstanceId());
        } catch (Exception saveEx) {
            log.error("保存设备命令失败状态异常", saveEx);
        }
    }

    /**
     * 脱敏错误信息：移除凭证、内部栈帧等敏感内容，保留错误类型和关键描述。
     */
    private String desensitizeError(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return e.getClass().getSimpleName();
        }
        // 移除可能包含的凭证信息（SecretId, SecretKey, password, token, AKID 等）
        String sanitized = message
                .replaceAll("(?i)SecretId\\s*=\\s*\\S+", "SecretId=***")
                .replaceAll("(?i)SecretKey\\s*=\\s*\\S+", "SecretKey=***")
                .replaceAll("(?i)password\\s*=\\s*\\S+", "password=***")
                .replaceAll("(?i)token\\s*=\\s*\\S+", "token=***")
                .replaceAll("(?i)AKID[A-Za-z0-9]+", "AKID***")
                .replaceAll("(?i)credential[^,;]*", "credential=***");
        // 截断到最大长度
        int maxLen = Math.min(sanitized.length(), 200);
        return sanitized.substring(0, maxLen);
    }
}
