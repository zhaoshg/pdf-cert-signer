package com.example.cert.core.common;

import lombok.Data;

import java.io.Serializable;

@Data
public class R<T> implements Serializable {

    private static final int SUCCESS_CODE = 0;
    private static final String SUCCESS_MSG = "success";

    private int code;
    private String message;
    private T data;

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = SUCCESS_CODE;
        r.message = SUCCESS_MSG;
        r.data = data;
        return r;
    }

    public static <T> R<T> ok(String message, T data) {
        R<T> r = new R<>();
        r.code = SUCCESS_CODE;
        r.message = message;
        r.data = data;
        return r;
    }

    public static <T> R<T> fail(int code, String message) {
        R<T> r = new R<>();
        r.code = code;
        r.message = message;
        return r;
    }

    public static <T> R<T> fail(String message) {
        return fail(1, message);
    }
}
