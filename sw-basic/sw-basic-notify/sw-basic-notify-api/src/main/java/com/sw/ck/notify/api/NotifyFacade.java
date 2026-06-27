package com.sw.ck.notify.api;

/**
 * 通知门面接口。
 * <p>
 * 定义于 {@code -api} 模块，实现于 {@code -biz} 模块。
 * 调用方经 Spring 容器注入本接口，不依赖实现细节。
 * </p>
 */
public interface NotifyFacade {

    /**
     * 发送通知。
     * <p>
     * 将业务通知持久化为 {@code sw_notify_message} 记录。
     * {@code tenant_id / create_time / create_by / deleted / version}
     * 由 MyBatis-Plus 拦截器自动注入。
     * </p>
     *
     * @param cmd 通知命令，不可为空
     */
    void send(SendNotifyCommand cmd);
}
