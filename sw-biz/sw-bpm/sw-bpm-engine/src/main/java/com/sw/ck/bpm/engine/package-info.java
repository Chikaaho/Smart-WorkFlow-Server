/**
 * BPM 流程引擎核心能力封包（闭源）。
 * <p>
 * 本模块封装 Flowable 等流程引擎实现，实现 {@code com.sw.ck.bpm.api.facade} 中的
 * Facade 接口。运行时通过 Spring 注入到 sw-bpm-process 等消费方。
 * 编译期 sw-bpm-process 不依赖本模块（open-core 模式）。
 * </p>
 *
 * @since 1.0.0
 */
package com.sw.ck.bpm.engine;
