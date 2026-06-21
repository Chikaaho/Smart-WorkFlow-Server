package com.sw.ck.notify.api;

/**
 * 通知目标解析器 SPI。
 * <p>
 * 将用户 ID 解析为通知目标（邮箱、手机号等）。
 * 由 sw-biz-system-biz 提供实现，sw-basic-notify-biz 消费。
 * <p>
 * 本接口定义在 -api 模块，确保 notify 不直接依赖 system。
 */
public interface NotifyTargetResolver {

    /**
     * 根据用户 ID 解析邮箱地址。
     *
     * @param userId 用户 ID
     * @return 邮箱地址，无法解析时返回 null
     */
    String resolveEmail(Long userId);

    /**
     * 根据用户 ID 解析手机号。
     *
     * @param userId 用户 ID
     * @return 手机号，无法解析时返回 null
     */
    String resolvePhone(Long userId);
}
