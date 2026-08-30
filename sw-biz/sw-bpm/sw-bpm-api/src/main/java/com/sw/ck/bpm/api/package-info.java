/**
 * BPM 流程引擎模块 - 对外 API 层（开源）。
 * <p>
 * 本模块仅包含 Facade 接口、DTO、契约常量，不含任何流程引擎实现类。
 * 所有接口不含 Flowable 类型引用，确保 api 层契约纯净。
 * 实现类位于 sw-bpm-engine（闭源），由 Spring 运行时注入。
 * </p>
 */
package com.sw.ck.bpm.api;
