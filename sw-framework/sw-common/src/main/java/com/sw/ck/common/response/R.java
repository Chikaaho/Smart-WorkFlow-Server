package com.sw.ck.common.response;

import lombok.Data;

import java.io.Serializable;

@Data
public class R<T> implements Serializable {

    public static final int SUCCESS_CODE = 0;
    public static final int FAIL_CODE = 1;

    private int code;
    private String msg;
    private T data;

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.setCode(SUCCESS_CODE);
        r.setMsg("success");
        r.setData(data);
        return r;
    }

    public static <T> R<T> fail(String msg) {
        return fail(FAIL_CODE, msg);
    }

    public static <T> R<T> fail(int code, String msg) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMsg(msg);
        return r;
    }
}
