package com.sw.ck.iot.api.impl;

import com.sw.ck.iot.api.IotDeviceFacade;
import com.sw.ck.iot.entity.IotDeviceCommand;
import com.sw.ck.iot.service.IotDeviceService;
import org.springframework.stereotype.Component;

/**
 * IoT 设备门面实现。
 * <p>
 * 设备身份固定为 {@code productId + deviceName}，委托 IotDeviceService 处理业务逻辑。
 * </p>
 */
@Component
public class IotDeviceFacadeImpl implements IotDeviceFacade {

    private final IotDeviceService iotDeviceService;

    public IotDeviceFacadeImpl(IotDeviceService iotDeviceService) {
        this.iotDeviceService = iotDeviceService;
    }

    @Override
    public Long dispatchCommand(String productId, String deviceName,
                                String commandKey, String commandType,
                                String payload, String approvalBizId) {
        IotDeviceCommand command = iotDeviceService.dispatchCommand(
                productId, deviceName, commandKey, commandType, payload, approvalBizId);
        return command.getId();
    }
}
