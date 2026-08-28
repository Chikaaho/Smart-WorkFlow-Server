package com.sw.ck.iot.service;

import com.sw.ck.iot.entity.IotDeviceCommand;
import com.sw.ck.iot.mapper.IotDeviceCommandMapper;
import com.sw.ck.iot.service.impl.CommandQueueServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommandQueueServiceTest {

    @Mock
    private IotDeviceCommandMapper commandMapper;

    @InjectMocks
    private CommandQueueServiceImpl commandQueueService;

    private IotDeviceCommand testCommand;

    @BeforeEach
    void setUp() {
        testCommand = new IotDeviceCommand();
        testCommand.setId(1L);
        testCommand.setProductId("test-product");
        testCommand.setDeviceName("test-device");
        testCommand.setCommandType("PROPERTY");
        testCommand.setCommandKey("control_property");
        testCommand.setSemanticMode("DEFERRED");
        testCommand.setPayload("{\"power\":true}");
        testCommand.setIdempotentKey("test-idempotent-key");
        testCommand.setExpiryTime(LocalDateTime.now().plusHours(24));
        testCommand.setRetryCount(0);
        testCommand.setStatus("QUEUED");
    }

    @Test
    void testEnqueue_Success() {
        when(commandMapper.insert(any(IotDeviceCommand.class))).thenReturn(1);

        IotDeviceCommand result = commandQueueService.enqueue(testCommand);

        assertNotNull(result);
        assertEquals("QUEUED", result.getStatus());
        assertEquals(0, result.getRetryCount());
        verify(commandMapper).insert(any(IotDeviceCommand.class));
    }

    @Test
    void testMarkSending_Success() {
        when(commandMapper.selectById(1L)).thenReturn(testCommand);
        when(commandMapper.updateById(any(IotDeviceCommand.class))).thenReturn(1);

        IotDeviceCommand result = commandQueueService.markSending(1L);

        assertNotNull(result);
        assertEquals("SENDING", result.getStatus());
        verify(commandMapper).updateById(any(IotDeviceCommand.class));
    }

    @Test
    void testMarkSent_Success() {
        when(commandMapper.selectById(1L)).thenReturn(testCommand);
        when(commandMapper.updateById(any(IotDeviceCommand.class))).thenReturn(1);

        IotDeviceCommand result = commandQueueService.markSent(1L, "req-123");

        assertNotNull(result);
        assertEquals("SENT", result.getStatus());
        assertEquals("req-123", result.getTencentRequestId());
        verify(commandMapper).updateById(any(IotDeviceCommand.class));
    }

    @Test
    void testMarkFailed_Success() {
        when(commandMapper.selectById(1L)).thenReturn(testCommand);
        when(commandMapper.updateById(any(IotDeviceCommand.class))).thenReturn(1);

        IotDeviceCommand result = commandQueueService.markFailed(1L, "设备离线");

        assertNotNull(result);
        assertEquals("FAILED", result.getStatus());
        assertEquals("设备离线", result.getLastError());
        assertEquals(1, result.getRetryCount());
        verify(commandMapper).updateById(any(IotDeviceCommand.class));
    }

    @Test
    void testMarkExpired_Success() {
        when(commandMapper.selectById(1L)).thenReturn(testCommand);
        when(commandMapper.updateById(any(IotDeviceCommand.class))).thenReturn(1);

        IotDeviceCommand result = commandQueueService.markExpired(1L);

        assertNotNull(result);
        assertEquals("EXPIRED", result.getStatus());
        verify(commandMapper).updateById(any(IotDeviceCommand.class));
    }
}
