package com.sw.ck.form.api.dto;

/**
 * 过滤操作符。
 *
 * <p>v1 支持 EQ / LIKE / GE / LE；IN 为契约预留，v1 不实现。</p>
 */
public enum FilterOp {

    EQ,
    LIKE,
    GE,
    LE,

    /**
     * v1 预留，暂不支持。
     */
    IN
}
