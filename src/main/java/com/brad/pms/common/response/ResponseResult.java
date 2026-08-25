package com.brad.pms.common.response;

import lombok.Getter;

/**
 * 统一返回结构（CQRS 响应风格）
 */
@Getter
public class ResponseResult<T> {

    public static final int SUCCESS = 200;
    public static final int ERROR = 500;
    public static final int PARAM_ERROR = 400;
    public static final int UNAUTHORIZED = 401;

    private final int code;
    private final String msg;
    private final T data;

    private ResponseResult(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> ResponseResult<T> success() {
        return new ResponseResult<>(SUCCESS, "操作成功", null);
    }

    public static <T> ResponseResult<T> success(T data) {
        return new ResponseResult<>(SUCCESS, "操作成功", data);
    }

    public static <T> ResponseResult<T> error(String msg) {
        return new ResponseResult<>(ERROR, msg, null);
    }

    public static <T> ResponseResult<T> error(int code, String msg) {
        return new ResponseResult<>(code, msg, null);
    }
}
