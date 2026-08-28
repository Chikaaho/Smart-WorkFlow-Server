package com.sw.ck.iot.util;

import com.sw.ck.iot.entity.IotDevice;
import com.sw.ck.iot.entity.IotDeviceCommand;
import com.sw.ck.iot.provider.DeviceControlProvider;
import com.sw.ck.iot.service.CommandQueueService;
import com.sw.ck.iot.service.IotDeviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeferredControlUtilTest {

    @Mock
    private IotDeviceService iotDeviceService;

    @Mock
    private CommandQueueService commandQueueService;

    @Mock
    private DeviceControlProvider deviceControlProvider;

    @InjectMocks
    private DeferredControlUtil deferredControlUtil;

    private IotDevice testDevice;

    @BeforeEach
    void setUp() {
        testDevice = new IotDevice();
        testDevice.setId(1L);
        testDevice.setProductId("test-product");
        testDevice.setDeviceName("test-device");
        testDevice.setDeviceKey("test-device-key");
        testDevice.setStatus("OFFLINE");
    }

    @Test
    void testControlProperty_DeviceOffline() {
        when(iotDeviceService.getByProductAndDeviceName("test-product", "test-device")).thenReturn(testDevice);
        when(commandQueueService.enqueue(any(IotDeviceCommand.class))).thenAnswer(invocation -> {
            IotDeviceCommand cmd = invocation.getArgument(0);
            cmd.setId(1L);
            cmd.setStatus("QUEUED");
            return cmd;
        });
        when(deviceControlProvider.queryDeviceStatus("test-product", "test-device")).thenReturn("offline");

        IotDeviceCommand result = deferredControlUtil.controlProperty(
                "test-product", "test-device", "{\"power\":true}", null);

        assertNotNull(result);
        assertEquals("QUEUED", result.getStatus());
        verify(commandQueueService).enqueue(any(IotDeviceCommand.class));
        verify(deviceControlProvider).queryDeviceStatus("test-product", "test-device");
    }

    @Test
    void testControlProperty_DeviceOnline() {
        when(iotDeviceService.getByProductAndDeviceName("test-product", "test-device")).thenReturn(testDevice);
        when(commandQueueService.enqueue(any(IotDeviceCommand.class))).thenAnswer(invocation -> {
            IotDeviceCommand cmd = invocation.getArgument(0);
            cmd.setId(1L);
            return cmd;
        });
        when(deviceControlProvider.queryDeviceStatus("test-product", "test-device")).thenReturn("online");
        when(deviceControlProvider.controlDeviceData("test-product", "test-device", "{\"power\":true}"))
                .thenReturn(DeviceControlProvider.DeviceControlResult.success("req-123"));
        when(commandQueueService.markSending(1L)).thenAnswer(invocation -> {
            IotDeviceCommand cmd = new IotDeviceCommand();
            cmd.setId(1L);
            cmd.setStatus("SENDING");
            return cmd;
        });
        when(commandQueueService.markSent(1L, "req-123")).thenAnswer(invocation -> {
            IotDeviceCommand cmd = new IotDeviceCommand();
            cmd.setId(1L);
            cmd.setStatus("SENT");
            return cmd;
        });

        IotDeviceCommand result = deferredControlUtil.controlProperty(
                "test-product", "test-device", "{\"power\":true}", null);

        assertNotNull(result);
        verify(commandQueueService).enqueue(any(IotDeviceCommand.class));
        verify(deviceControlProvider).controlDeviceData("test-product", "test-device", "{\"power\":true}");
    }

    @Test
    void testControlProperty_DeviceNotFound() {
        when(iotDeviceService.getByProductAndDeviceName("test-product", "test-device")).thenReturn(null);

        assertThrows(com.sw.ck.common.exception.BaseException.class, () ->
                deferredControlUtil.controlProperty(
                        "test-product", "test-device", "{\"power\":true}", null));
    }
}
