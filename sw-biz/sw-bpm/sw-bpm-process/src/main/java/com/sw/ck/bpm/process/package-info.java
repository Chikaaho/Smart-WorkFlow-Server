/**
 * BPM 业务处理层（开源）。
 * <p>
 * 负责流程编排、待办富化、表单绑定等业务处理逻辑。
 * 编译期仅依赖 sw-bpm-api，不依赖 sw-bpm-engine（open-core 模式）。
 * 运行时通过 Spring 注入 sw-bpm-engine 提供的 Facade 实现。
 * </p>
 *
 * @since 1.0.0
 */
package com.sw.ck.bpm.process;
