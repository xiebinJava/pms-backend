package com.brad.pms.common.exception;

import lombok.Getter;

/**
 * 业务异常（CQRS 风格异常）
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        this(ResponseCode.ERROR, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BusinessException error(String message) {
        return new BusinessException(message);
    }

    public static BusinessException unauthorized(String message) {
        return new BusinessException(401, message);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(403, message);
    }

    public static class ResponseCode {
        public static final int ERROR = 500;
        public static final int UNAUTHORIZED = 401;
        public static final int FORBIDDEN = 403;
    }
}
