/**
 * 低代码表单模块 - 对外 API 层。
 * <p>
 * 本模块仅包含 DTO、SPI 接口、表单事件、契约常量，不含 Spring Web 或 DB 实现。
 * 表单提交事件 ({@code FormSubmittedEvent}) 在此模块定义，供流程模块通过事件监听解耦。
 */
package com.sw.ck.form.api;
