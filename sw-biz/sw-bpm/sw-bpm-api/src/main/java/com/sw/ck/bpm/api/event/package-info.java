/**
 * BPM 事件包（开源）。
 * <p>
 * 定义 BPM 流程通知事件，由 bpm-process 发布，
 * 经 {@code DomainEventPublisher} + {@code @TransactionalEventListener(AFTER_COMMIT)}
 * 异步消费。
 * </p>
 */
package com.sw.ck.bpm.api.event;
