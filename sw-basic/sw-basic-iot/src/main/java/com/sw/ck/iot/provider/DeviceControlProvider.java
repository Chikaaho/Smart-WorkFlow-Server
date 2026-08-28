package com.sw.ck.iot.provider;

/**
 * 设备控制提供者接口。
 * <p>
 * 定义腾讯云 IoT Explorer SDK 的设备控制抽象，支持 Mock 和 Tencent 两种实现。
 * </p>
 */
public interface DeviceControlProvider {

    /**
     * 查询设备在线状态。
     *
     * @param productId  腾讯云产品 ID
     * @param deviceName 腾讯云设备名称
     * @return 设备在线状态（online / offline / not_active / error）
     */
    String queryDeviceStatus(String productId, String deviceName);

    /**
     * 属性下发（ControlDeviceData）。
     *
     * @param productId   腾讯云产品 ID
     * @param deviceName  腾讯云设备名称
     * @param propertyJson 属性 JSON
     * @return 控制结果（包含 RequestId 等信息）
     */
    DeviceControlResult controlDeviceData(String productId, String deviceName, String propertyJson);

    /**
     * 同步行为调用（CallDeviceActionSync）。
     *
     * @param productId  腾讯云产品 ID
     * @param deviceName 腾讯云设备名称
     * @param actionId   行为 ID
     * @param inputJson  输入参数 JSON
     * @return 控制结果（包含设备输出参数）
     */
    DeviceControlResult callDeviceActionSync(String productId, String deviceName,
                                             String actionId, String inputJson);

    /**
     * 设备控制结果。
     */
    record DeviceControlResult(
            boolean success,
            String requestId,
            String clientToken,
            String deviceOutput,
            String errorMessage
    ) {
        public static DeviceControlResult success(String requestId) {
            return new DeviceControlResult(true, requestId, null, null, null);
        }

        public static DeviceControlResult success(String requestId, String deviceOutput) {
            return new DeviceControlResult(true, requestId, null, deviceOutput, null);
        }

        public static DeviceControlResult success(String requestId, String clientToken, String deviceOutput) {
            return new DeviceControlResult(true, requestId, clientToken, deviceOutput, null);
        }

        public static DeviceControlResult failure(String errorMessage) {
            return new DeviceControlResult(false, null, null, null, errorMessage);
        }
    }
}
