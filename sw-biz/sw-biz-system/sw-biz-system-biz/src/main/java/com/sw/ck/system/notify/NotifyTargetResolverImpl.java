package com.sw.ck.system.notify;

import com.sw.ck.notify.api.NotifyTargetResolver;
import org.springframework.stereotype.Component;

/**
 * NotifyTargetResolver SPI 实现。
 * <p>
 * 由 sw-biz-system-biz 提供，将用户 ID 解析为真实邮箱/手机号。
 * 本类为骨架占位，后续接入实际用户数据后完善。
 */
@Component
public class NotifyTargetResolverImpl implements NotifyTargetResolver {

    @Override
    public String resolveEmail(Long userId) {
        // TODO: 从组织架构用户表查询邮箱
        return null;
    }

    @Override
    public String resolvePhone(Long userId) {
        // TODO: 从组织架构用户表查询手机号
        return null;
    }
}
