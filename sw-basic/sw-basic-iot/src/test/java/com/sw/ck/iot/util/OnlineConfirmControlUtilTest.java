package com.sw.ck.iot.util;

import com.sw.ck.common.exception.BaseException;
import com.sw.ck.iot.entity.IotDevice;
import com.sw.ck.iot.entity.IotDeviceCommand;
import com.sw.ck.iot.provider.DeviceControlProvider;
import com.sw.ck.iot.service.IotDeviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OnlineConfirmControlUtilTest {

    @Mock
    private IotDeviceService iotDeviceService;

    @Mock
    private DeviceControlProvider deviceControlProvider;

    @InjectMocks
    private OnlineConfirmControlUtil onlineConfirmControlUtil;

    private IotDevice testDevice;

    @BeforeEach
    void setUp() {
        testDevice = new IotDevice();
        testDevice.setId(1L);
        testDevice.setProductId("test-product");
        testDevice.setDeviceName("test-device");
        testDevice.setDeviceKey("test-device-key");
        testDevice.setStatus("ONLINE");
    }

    @Test
    void testControlPropertyOnline_DeviceOffline() {
        when(deviceControlProvider.queryDeviceStatus("test-product", "test-device")).thenReturn("offline");

        assertThrows(BaseException.class, () ->
                onlineConfirmControlUtil.controlPropertyOnline(
                        "test-product", "test-device", "{\"power\":true}"));
    }

    @Test
    void testControlPropertyOnline_DeviceOnline() {
        when(deviceControlProvider.queryDeviceStatus("test-product", "test-device")).thenReturn("online");
        when(deviceControlProvider.controlDeviceData("test-product", "test-device", "{\"power\":true}"))
                .thenReturn(DeviceControlProvider.DeviceControlResult.success("req-123"));
        when(iotDeviceService.getByProductAndDeviceName("test-product", "test-device")).thenReturn(testDevice);

        IotDeviceCommand result = onlineConfirmControlUtil.controlPropertyOnline(
                "test-product", "test-device", "{\"power\":true}");

        assertNotNull(result);
        assertEquals("SENT", result.getStatus());
        assertEquals("req-123", result.getTencentRequestId());
        assertEquals("ONLINE_CONFIRM", result.getSemanticMode());
    }

    @Test
    void testControlPropertyOnline_TencentApiFailure() {
        when(deviceControlProvider.queryDeviceStatus("test-product", "test-device")).thenReturn("online");
        when(deviceControlProvider.controlDeviceData("test-product", "test-device", "{\"power\":true}"))
                .thenReturn(DeviceControlProvider.DeviceControlResult.failure("API 调用失败"));

        assertThrows(BaseException.class, () ->
                onlineConfirmControlUtil.controlPropertyOnline(
                        "test-product", "test-device", "{\"power\":true}"));
    }

    @Test
    void testControlActionOnline_DeviceOffline() {
        when(deviceControlProvider.queryDeviceStatus("test-product", "test-device")).thenReturn("offline");

        assertThrows(BaseException.class, () ->
                onlineConfirmControlUtil.controlActionOnline(
                        "test-product", "test-device", "power_on", "{}"));
    }

    @Test
    void testControlActionOnline_DeviceOnline() {
        when(deviceControlProvider.queryDeviceStatus("test-product", "test-device")).thenReturn("online");
        when(deviceControlProvider.callDeviceActionSync("test-product", "test-device", "power_on", "{}"))
                .thenReturn(DeviceControlProvider.DeviceControlResult.success("req-123", "client-token", "{\"result\":\"ok\"}"));
        when(iotDeviceService.getByProductAndDeviceName("test-product", "test-device")).thenReturn(testDevice);

        IotDeviceCommand result = onlineConfirmControlUtil.controlActionOnline(
                "test-product", "test-device", "power_on", "{}");

        assertNotNull(result);
        assertEquals("ACKED", result.getStatus());
        assertEquals("req-123", result.getTencentRequestId());
        assertEquals("client-token", result.getClientToken());
        assertEquals("{\"result\":\"ok\"}", result.getDeviceOutput());
        assertEquals("ONLINE_CONFIRM", result.getSemanticMode());
    }
}
