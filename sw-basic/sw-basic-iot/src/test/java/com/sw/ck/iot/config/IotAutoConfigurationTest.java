package com.sw.ck.iot.config;

import com.sw.ck.iot.provider.DeviceControlProvider;
import com.sw.ck.iot.provider.MockCloudProvider;
import com.sw.ck.iot.provider.TencentCloudProvider;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockConstruction;

/**
 * G1 聚焦测试：验证 Provider 选择行为。
 * <p>
 * 场景1: providerMode=mock → MockCloudProvider
 * 场景2: providerMode=tencent + 完整凭证 → TencentCloudProvider
 * 场景3: providerMode=tencent + 缺凭证 → IllegalStateException，不创建 Mock
 */
class IotAutoConfigurationTest {

    private final IotAutoConfiguration config = new IotAutoConfiguration();

    @Test
    void testMockMode_createsMockProvider() {
        TencentCloudProperties props = new TencentCloudProperties();
        props.setProviderMode("mock");

        DeviceControlProvider provider = config.deviceControlProvider(props);

        assertInstanceOf(MockCloudProvider.class, provider,
                "providerMode=mock 应创建 MockCloudProvider");
        assertFalse(provider instanceof TencentCloudProvider,
                "providerMode=mock 不应创建 TencentCloudProvider");
    }

    @Test
    void testTencentMode_withCredentials_createsTencentProvider() {
        TencentCloudProperties props = new TencentCloudProperties();
        props.setProviderMode("tencent");
        props.setSecretId("AKIDtest123456");
        props.setSecretKey("testSecretKey789");

        DeviceControlProvider provider = config.deviceControlProvider(props);

        assertInstanceOf(TencentCloudProvider.class, provider,
                "providerMode=tencent + 完整凭证应创建 TencentCloudProvider");
        assertFalse(provider instanceof MockCloudProvider,
                "providerMode=tencent + 完整凭证不应创建 MockCloudProvider");
    }

    @Test
    void testTencentMode_withoutCredentials_throwsIllegalState() {
        TencentCloudProperties props = new TencentCloudProperties();
        props.setProviderMode("tencent");
        // 不设置 secretId 和 secretKey

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> config.deviceControlProvider(props),
                "providerMode=tencent + 缺凭证应抛 IllegalStateException");

        assertTrue(ex.getMessage().contains("SecretId"),
                "异常信息应提及 SecretId");
        assertTrue(ex.getMessage().contains("mock"),
                "异常信息应建议切换为 mock 模式");
    }

    /**
     * R1 反向零残留断言：providerMode=tencent 且缺凭证时，
     * 除抛出 IllegalStateException 外，MockCloudProvider 构造/工厂调用
     * 次数严格为零，不得返回任何 Mock 实例兜底。
     */
    @Test
    void testTencentMode_withoutCredentials_neverConstructsMockProvider() {
        TencentCloudProperties props = new TencentCloudProperties();
        props.setProviderMode("tencent");
        // 不设置 secretId 和 secretKey

        try (MockedConstruction<MockCloudProvider> mocked = mockConstruction(MockCloudProvider.class)) {
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> config.deviceControlProvider(props),
                    "providerMode=tencent + 缺凭证应抛 IllegalStateException");

            assertTrue(ex.getMessage().contains("SecretId"),
                    "异常信息应提及 SecretId");

            // 反向断言：Mock 构造次数严格为零，无任何 Mock 实例被创建或返回
            assertEquals(0, mocked.constructed().size(),
                    "缺凭证时 MockCloudProvider 构造/工厂调用次数必须严格为 0");
            assertTrue(mocked.constructed().isEmpty(),
                    "不得创建或返回任何 MockCloudProvider 实例");
        }
    }
}
