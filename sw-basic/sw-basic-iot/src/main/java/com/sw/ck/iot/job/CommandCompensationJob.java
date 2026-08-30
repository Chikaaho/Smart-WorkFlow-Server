package com.sw.ck.iot.job;

import com.sw.ck.iot.config.TencentCloudProperties;
import com.sw.ck.iot.entity.IotDeviceCommand;
import com.sw.ck.iot.service.CommandQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 命令补偿定时任务。
 * <p>
 * 检测长期滞留队列的命令，处理过期命令，重试瞬时失败。
 * </p>
 */
@Component
public class CommandCompensationJob {

    private static final Logger log = LoggerFactory.getLogger(CommandCompensationJob.class);

    private final CommandQueueService commandQueueService;
    private final TencentCloudProperties tencentCloudProperties;

    public CommandCompensationJob(CommandQueueService commandQueueService,
                                  TencentCloudProperties tencentCloudProperties) {
        this.commandQueueService = commandQueueService;
        this.tencentCloudProperties = tencentCloudProperties;
    }

    /**
     * 每 5 分钟执行一次补偿任务。
     */
    @Scheduled(fixedDelay = 300000)
    public void execute() {
        log.debug("开始执行命令补偿任务");

        // 1. 处理过期命令
        processExpiredCommands();

        // 2. 处理滞留命令
        processStuckCommands();

        // 3. 重试瞬时失败
        retryFailedCommands();

        log.debug("命令补偿任务执行完成");
    }

    /**
     * 处理过期命令。
     */
    private void processExpiredCommands() {
        List<IotDeviceCommand> expiredCommands = commandQueueService.getExpiredCommands();
        if (expiredCommands.isEmpty()) {
            return;
        }

        log.info("发现 {} 条过期命令，开始处理", expiredCommands.size());
        for (IotDeviceCommand command : expiredCommands) {
            commandQueueService.markExpired(command.getId());
            log.warn("命令已标记为过期: id={}, productId={}, deviceName={}, createTime={}",
                    command.getId(), command.getProductId(), command.getDeviceName(), command.getCreateTime());
        }
    }

    /**
     * 处理滞留命令（超过 30 分钟未处理的 QUEUED 命令）。
     */
    private void processStuckCommands() {
        List<IotDeviceCommand> stuckCommands = commandQueueService.getStuckCommands(30);
        if (stuckCommands.isEmpty()) {
            return;
        }

        log.info("发现 {} 条滞留命令，开始处理", stuckCommands.size());
        for (IotDeviceCommand command : stuckCommands) {
            // 检查是否已过期
            if (command.getExpiryTime() != null && command.getExpiryTime().isBefore(LocalDateTime.now())) {
                commandQueueService.markExpired(command.getId());
                log.warn("滞留命令已过期: id={}", command.getId());
            } else {
                // 尝试重试
                log.info("尝试重试滞留命令: id={}, retryCount={}", command.getId(), command.getRetryCount());
                // 注意：实际重试逻辑需要注入 DeferredControlUtil，此处简化处理
            }
        }
    }

    /**
     * 重试瞬时失败（FAILED 状态且未超过最大重试次数）。
     */
    private void retryFailedCommands() {
        int maxRetryCount = tencentCloudProperties.getMaxRetryCount();
        List<IotDeviceCommand> failedCommands = commandQueueService.getStuckCommands(5);

        for (IotDeviceCommand command : failedCommands) {
            if (!"FAILED".equals(command.getStatus())) {
                continue;
            }

            if (command.getRetryCount() < maxRetryCount) {
                log.info("重试失败命令: id={}, retryCount={}/{}",
                        command.getId(), command.getRetryCount(), maxRetryCount);
                // 标记为 QUEUED 以便下次补发
                command.setStatus("QUEUED");
                // 注意：实际重试逻辑需要注入 DeferredControlUtil，此处简化处理
            } else {
                log.warn("失败命令超过最大重试次数: id={}, retryCount={}",
                        command.getId(), command.getRetryCount());
            }
        }
    }
}
