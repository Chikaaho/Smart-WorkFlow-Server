package com.sw.ck.common.enums;

import lombok.Getter;

@Getter
public enum CommonStatusEnum {

    ENABLE(1, "启用"),
    DISABLE(0, "停用"),
    ;

    private final int value;
    private final String label;

    CommonStatusEnum(int value, String label) {
        this.value = value;
        this.label = label;
    }
}
