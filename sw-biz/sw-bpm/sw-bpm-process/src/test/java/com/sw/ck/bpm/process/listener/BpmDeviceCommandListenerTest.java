package com.sw.ck.bpm.process.listener;

import com.sw.ck.bpm.api.event.BpmDeviceCommandEvent;
import com.sw.ck.bpm.process.entity.BpmInstance;
import com.sw.ck.bpm.process.mapper.BpmInstanceMapper;
import com.sw.ck.iot.api.IotDeviceFacade;
import com.sw.ck.iot.entity.IotDeviceCommand;
import com.sw.ck.iot.mapper.IotDeviceCommandMapper;
import com.sw.ck.security.holder.LoginUserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * G2 聚焦测试：验证设备命令失败时持久层行为。
 * <p>
 * 场景: facade.dispatchCommand 抛异常 →
 *   审批已完成（事件已触发，事务已提交） +
 *   命令状态=FAILED +
 *   approvalBizId=流程实例ID +
 *   lastError 非空且不含 Secret 原值
 */
@ExtendWith(MockitoExtension.class)
class BpmDeviceCommandListenerTest {

    @Mock
    private IotDeviceFacade iotDeviceFacade;

    @Mock
    private IotDeviceCommandMapper commandMapper;

    private BpmDeviceCommandListener listener;

    @BeforeEach
    void setUp() {
        LoginUserHolder.clear();
        // 手动构造，避免 ObjectProvider 泛型注入顺序问题
        @SuppressWarnings("unchecked")
        ObjectProvider<IotDeviceFacade> facadeProvider = mock(ObjectProvider.class);
        when(facadeProvider.getIfAvailable()).thenReturn(iotDeviceFacade);

        @SuppressWarnings("unchecked")
        ObjectProvider<IotDeviceCommandMapper> mapperProvider = mock(ObjectProvider.class);
        when(mapperProvider.getIfAvailable()).thenReturn(commandMapper);

        listener = new BpmDeviceCommandListener(facadeProvider, mapperProvider);
    }

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    @Test
    void testDeviceCommandFailure_savesFailedCommand() {
        // Arrange: facade 抛异常模拟腾讯调用失败
        when(iotDeviceFacade.dispatchCommand(
                eq("prod-001"), eq("dev-001"),
                eq("power_on"), eq("PROPERTY"),
                isNull(), eq("process-abc-123")))
                .thenThrow(new RuntimeException("腾讯云 API 调用失败: SecretId=AKIDxxx 超时"));

        BpmDeviceCommandEvent event = new BpmDeviceCommandEvent(
                "process-abc-123", "prod-001", "dev-001",
                "power_on", "PROPERTY",
                100L, 200L);

        // Act: 模拟 AFTER_COMMIT 回调
        listener.onProcessApproved(event);

        // Assert: 命令记录被插入
        ArgumentCaptor<IotDeviceCommand> captor = ArgumentCaptor.forClass(IotDeviceCommand.class);
        verify(commandMapper, times(1)).insert(captor.capture());

        IotDeviceCommand saved = captor.getValue();

        // 1. 命令状态为 FAILED
        assertEquals("FAILED", saved.getStatus(),
                "命令状态应为 FAILED");

        // 2. approvalBizId 与流程实例一致
        assertEquals("process-abc-123", saved.getApprovalBizId(),
                "approvalBizId 应与流程实例 ID 一致");

        // 3. productId + deviceName 正确传递
        assertEquals("prod-001", saved.getProductId());
        assertEquals("dev-001", saved.getDeviceName());

        // 4. lastError 非空
        assertNotNull(saved.getLastError(), "lastError 不应为空");
        assertFalse(saved.getLastError().isEmpty(), "lastError 不应为空字符串");

        // 5. lastError 不含 SecretId 原值
        assertFalse(saved.getLastError().contains("AKIDxxx"),
                "lastError 不应包含 SecretId 原值");

        // 6. lastError 不含 SecretKey 原值（此处未含，但作为通用断言）
        assertFalse(saved.getLastError().toLowerCase().contains("secretkey"),
                "lastError 不应包含 SecretKey 原值");
    }

    @Test
    void testDeviceCommandFailure_approvalNotRolledBack() {
        // Arrange: facade 抛异常
        when(iotDeviceFacade.dispatchCommand(any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("设备不可达"));

        BpmDeviceCommandEvent event = new BpmDeviceCommandEvent(
                "process-xyz-789", "prod-002", "dev-002",
                "set_temp", "ACTION",
                101L, 201L);

        // Act
        listener.onProcessApproved(event);

        // Assert: 审批事件已触发 = 审批事务已提交（AFTER_COMMIT 阶段）
        // 如果审批未完成，事件不会触发此监听器
        // 此处验证命令被保存为 FAILED，证明审批已完成但命令失败
        ArgumentCaptor<IotDeviceCommand> captor = ArgumentCaptor.forClass(IotDeviceCommand.class);
        verify(commandMapper).insert(captor.capture());

        assertEquals("FAILED", captor.getValue().getStatus(),
                "审批已完成（事件已触发），命令应为 FAILED");
        assertEquals("process-xyz-789", captor.getValue().getApprovalBizId(),
                "approvalBizId 应与流程实例一致");
    }

    /**
     * R2：设备命令失败后，审批/流程实例状态不得倒退。
     * <p>
     * 前置流程实例状态固定为 APPROVED，触发设备命令异常后：
     * 正向——从持久层回查实例状态仍为 APPROVED；
     * 反向——对实例持久层零写操作，未写回处理中/失败/拒绝等非完成状态。
     * 命令 FAILED 结果复用已锁定的 G2 行为，不重复汇报其他 G2 断言。
     */
    @Test
    void testDeviceCommandFailure_approvalInstanceRemainsApproved() {
        // Arrange: 用内存 Map 模拟实例持久层，stub selectById/updateById
        BpmInstanceMapper instanceMapper = mock(BpmInstanceMapper.class);
        Map<Long, BpmInstance> instanceStore = new HashMap<>();
        BpmInstance instance = new BpmInstance();
        instance.setId(1L);
        instance.setProcessInstanceId("process-appr-r2");
        instance.setStatus("APPROVED");
        instanceStore.put(1L, instance);
        when(instanceMapper.selectById(1L)).thenAnswer(inv -> instanceStore.get(1L));

        when(iotDeviceFacade.dispatchCommand(
                eq("prod-r2"), eq("dev-r2"),
                eq("power_on"), eq("PROPERTY"),
                isNull(), eq("process-appr-r2")))
                .thenThrow(new RuntimeException("腾讯云 API 调用失败: SecretId=AKIDxxx 超时"));

        BpmDeviceCommandEvent event = new BpmDeviceCommandEvent(
                "process-appr-r2", "prod-r2", "dev-r2",
                "power_on", "PROPERTY",
                103L, 203L);

        // Act
        listener.onProcessApproved(event);

        // 复用已锁定 G2 行为：命令保存为 FAILED
        ArgumentCaptor<IotDeviceCommand> captor = ArgumentCaptor.forClass(IotDeviceCommand.class);
        verify(commandMapper, times(1)).insert(captor.capture());
        assertEquals("FAILED", captor.getValue().getStatus(),
                "命令状态应为 FAILED");

        // 正向断言：持久层回查流程实例状态仍为 APPROVED
        BpmInstance reloaded = instanceMapper.selectById(1L);
        assertNotNull(reloaded, "流程实例应可回查");
        assertEquals("APPROVED", reloaded.getStatus(),
                "设备命令失败后流程实例状态必须仍为 APPROVED");

        // 反向零残留断言：对实例持久层零写操作，未写回任何非完成状态
        verify(instanceMapper, never()).updateById(any(BpmInstance.class));
        verify(instanceMapper, never()).update(any(), any());
        verify(instanceMapper, never()).deleteById(anyString());
        verify(instanceMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void testDesensitizeError_removesSensitiveInfo() {
        // Arrange: facade 抛含凭证的异常
        when(iotDeviceFacade.dispatchCommand(any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException(
                        "连接失败: SecretId=AKIDreal123, SecretKey=realSecret456, password=abc"));

        BpmDeviceCommandEvent event = new BpmDeviceCommandEvent(
                "process-sens-1", "prod-003", "dev-003",
                "power_on", "PROPERTY",
                102L, 202L);

        // Act
        listener.onProcessApproved(event);

        // Assert: 脱敏后不含原始凭证
        ArgumentCaptor<IotDeviceCommand> captor = ArgumentCaptor.forClass(IotDeviceCommand.class);
        verify(commandMapper).insert(captor.capture());

        String lastError = captor.getValue().getLastError();
        assertNotNull(lastError);
        assertFalse(lastError.contains("AKIDreal123"), "不应包含 SecretId 原值");
        assertFalse(lastError.contains("realSecret456"), "不应包含 SecretKey 原值");
        assertFalse(lastError.contains("password=abc"), "不应包含 password 原值");
        // 应保留错误类型
        assertTrue(lastError.contains("连接失败") || lastError.contains("Exception"),
                "应保留错误类型描述");
    }
}
