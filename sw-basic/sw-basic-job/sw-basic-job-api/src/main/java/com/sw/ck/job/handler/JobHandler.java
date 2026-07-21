package com.sw.ck.job.handler;

/**
 * 定时任务处理器 SPI。
 * <p>
 * 业务方实现此接口并注册为 Spring Bean，即可通过 {@code BEAN} 类型定时任务调用。
 * 实现类通过 {@link #getName()} 返回的 bean 名称与 {@code JobInfo.beanName} 匹配。
 * </p>
 *
 * <h3>实现约定</h3>
 * <ul>
 *   <li>实现类标注 {@code @Component("myHandler")}，bean name 即为 handler 名称</li>
 *   <li>{@link #execute(String)} 接收 {@code JobInfo.beanParams}（JSON 字符串），无参数时传 {@code null}</li>
 *   <li>抛出任何异常均视为执行失败，由调度框架捕获并记录到 {@code sw_job_log}</li>
 *   <li>实现类应保证线程安全（Quartz 线程池中并发调用）</li>
 * </ul>
 */
public interface JobHandler {

    /**
     * 执行任务。
     *
     * @param params 任务参数（JSON 字符串，可为 null）
     * @throws Exception 执行失败时抛出
     */
    void execute(String params) throws Exception;

    /**
     * 获取处理器名称。
     * <p>
     * 返回值应与实现类的 Spring Bean 名称一致，用于与 {@code JobInfo.beanName} 匹配。
     * </p>
     *
     * @return 处理器名称（即 Spring Bean 名称）
     */
    String getName();
}
