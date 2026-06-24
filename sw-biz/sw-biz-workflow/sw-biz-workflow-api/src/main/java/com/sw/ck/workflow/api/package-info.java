/**
 * 流程引擎模块 - 对外 API 层。
 * <p>
 * 本模块仅包含 DTO、SPI 接口、流程事件、契约常量，不含 Spring Web 或 DB 实现。
 * 用户任务的表单渲染数据通过 sw-biz-form-api 获取，不直接依赖 form-biz。
 */
package com.sw.ck.workflow.api;
