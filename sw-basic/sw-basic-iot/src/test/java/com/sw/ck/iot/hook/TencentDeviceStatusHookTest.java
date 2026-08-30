package com.sw.ck.iot.hook;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.sw.ck.iot.entity.IotDevice;
import com.sw.ck.iot.service.IotDeviceService;
import com.sw.ck.iot.util.DeferredControlUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TencentDeviceStatusHookTest {

    @Mock
    private IotDeviceService iotDeviceService;

    @Mock
    private DeferredControlUtil deferredControlUtil;

    @InjectMocks
    private TencentDeviceStatusHook hook;

    private IotDevice testDevice;

    @BeforeEach
    void setUp() {
        testDevice = new IotDevice();
        testDevice.setId(1L);
        testDevice.setProductId("test-product");
        testDevice.setDeviceName("test-device");
        testDevice.setStatus("OFFLINE");
    }

    @Test
    void testVerifyTencentCallback() {
        String result = hook.verifyTencentCallback("test-echo");
        assertEquals("test-echo", result);
    }

    @Test
    void testHandleDeviceStatus_OnlineEvent() {
        when(iotDeviceService.getByProductAndDeviceName("test-product", "test-device")).thenReturn(testDevice);

        // 构造腾讯云回调格式：外层包含 ProductId, DeviceName, Payload 字段
        JSONObject eventPayload = new JSONObject();
        eventPayload.put("productID", "test-product");
        eventPayload.put("deviceName", "test-device");
        eventPayload.put("event", "EV_ONLINE");

        JSONObject wrapper = new JSONObject();
        wrapper.put("ProductId", "test-product");
        wrapper.put("DeviceName", "test-device");
        wrapper.put("Payload", eventPayload);

        Map<String, Boolean> result = hook.handleDeviceStatus(wrapper.toJSONString());

        assertTrue(result.get("result"));
        verify(iotDeviceService).updateById(any(IotDevice.class));
        verify(deferredControlUtil).flushDeviceCommands("test-product", "test-device");
    }

    @Test
    void testHandleDeviceStatus_OfflineEvent() {
        // Offline event should not trigger device update or flush
        JSONObject eventPayload = new JSONObject();
        eventPayload.put("productID", "test-product");
        eventPayload.put("deviceName", "test-device");
        eventPayload.put("event", "EV_OFFLINE");

        JSONObject wrapper = new JSONObject();
        wrapper.put("ProductId", "test-product");
        wrapper.put("DeviceName", "test-device");
        wrapper.put("Payload", eventPayload);

        Map<String, Boolean> result = hook.handleDeviceStatus(wrapper.toJSONString());

        assertTrue(result.get("result"));
        verify(iotDeviceService, never()).updateById(any());
        verify(deferredControlUtil, never()).flushDeviceCommands(anyString(), anyString());
    }

    @Test
    void testHandleDeviceStatus_DeviceNotFound() {
        when(iotDeviceService.getByProductAndDeviceName("test-product", "test-device")).thenReturn(null);

        JSONObject eventPayload = new JSONObject();
        eventPayload.put("productID", "test-product");
        eventPayload.put("deviceName", "test-device");
        eventPayload.put("event", "EV_ONLINE");

        JSONObject wrapper = new JSONObject();
        wrapper.put("ProductId", "test-product");
        wrapper.put("DeviceName", "test-device");
        wrapper.put("Payload", eventPayload);

        Map<String, Boolean> result = hook.handleDeviceStatus(wrapper.toJSONString());

        // 即使设备不存在，也返回 true（幂等处理）
        assertTrue(result.get("result"));
        verify(deferredControlUtil, never()).flushDeviceCommands(anyString(), anyString());
    }

    @Test
    void testHandleDeviceStatus_MissingProductId() {
        JSONObject wrapper = new JSONObject();
        wrapper.put("DeviceName", "test-device");

        Map<String, Boolean> result = hook.handleDeviceStatus(wrapper.toJSONString());

        assertFalse(result.get("result"));
        verify(iotDeviceService, never()).updateById(any());
    }

    @Test
    void testHandleDeviceStatus_Base64Payload() {
        when(iotDeviceService.getByProductAndDeviceName("test-product", "test-device")).thenReturn(testDevice);

        // 构造 Base64 编码的 Payload（对齐 Demo：腾讯云回调中 Payload 字段可为 Base64 字符串）
        String jsonPayload = "{\"productID\":\"test-product\",\"deviceName\":\"test-device\",\"event\":\"EV_ONLINE\"}";
        String base64Payload = Base64.getEncoder().encodeToString(jsonPayload.getBytes());

        // 腾讯云回调格式：外层包含 ProductId, DeviceName, Payload 字段
        JSONObject wrapper = new JSONObject();
        wrapper.put("ProductId", "test-product");
        wrapper.put("DeviceName", "test-device");
        wrapper.put("Payload", base64Payload);  // Payload 为 Base64 编码的字符串

        Map<String, Boolean> result = hook.handleDeviceStatus(wrapper.toJSONString());

        assertTrue(result.get("result"));
        verify(iotDeviceService).updateById(any(IotDevice.class));
    }
}
