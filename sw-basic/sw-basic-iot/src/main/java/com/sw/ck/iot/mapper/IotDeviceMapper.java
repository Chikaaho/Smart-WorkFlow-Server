package com.sw.ck.iot.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sw.ck.common.mapper.BaseMapperX;
import com.sw.ck.iot.entity.IotDevice;
import org.apache.ibatis.annotations.Mapper;

/**
 * IoT 设备 Mapper。
 * <p>
 * 支持按 productId + deviceName 复合身份查询设备。
 * </p>
 */
@Mapper
public interface IotDeviceMapper extends BaseMapperX<IotDevice> {

    /**
     * 按腾讯云产品 ID 和设备名称查询设备（租户隔离）。
     *
     * @param productId  腾讯云产品 ID
     * @param deviceName 腾讯云设备名称
     * @param tenantId   租户 ID
     * @return 设备实体，不存在时返回 null
     */
    default IotDevice selectByProductAndDeviceName(String productId, String deviceName, Long tenantId) {
        return selectOne(new LambdaQueryWrapper<IotDevice>()
                .eq(IotDevice::getProductId, productId)
                .eq(IotDevice::getDeviceName, deviceName)
                .eq(IotDevice::getTenantId, tenantId)
                .eq(IotDevice::getDeleted, 0));
    }
}
