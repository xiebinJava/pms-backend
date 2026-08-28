package com.brad.pms.common.exception;

import com.brad.pms.common.response.ResponseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;

/**
 * 全局异常处理（统一异常处理）
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ResponseResult<Void>> handleBusiness(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return response(e.getCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ResponseResult<Void>> handleBind(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError == null ? "参数校验失败" : fieldError.getDefaultMessage();
        return response(ResponseResult.PARAM_ERROR, msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ResponseResult<Void>> handleConstraint(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse("参数校验失败");
        return response(ResponseResult.PARAM_ERROR, msg);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ResponseResult<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        return response(ResponseResult.PARAM_ERROR, "缺少参数: " + e.getParameterName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResponseResult<Void>> handleUnreadableMessage(HttpMessageNotReadableException e) {
        return response(ResponseResult.PARAM_ERROR, "请求参数格式不正确");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseResult<Void>> handleException(Exception e) {
        log.error("系统异常", e);
        return response(ResponseResult.ERROR, "系统繁忙，请稍后重试");
    }

    private ResponseEntity<ResponseResult<Void>> response(int code, String message) {
        HttpStatus status = httpStatus(code);
        return ResponseEntity.status(status).body(ResponseResult.error(code, message));
    }

    static HttpStatus httpStatus(int code) {
        return switch (code) {
            case ResponseResult.PARAM_ERROR -> HttpStatus.BAD_REQUEST;
            case ResponseResult.UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case ResponseResult.FORBIDDEN -> HttpStatus.FORBIDDEN;
            case ResponseResult.CONFLICT -> HttpStatus.CONFLICT;
            case ResponseResult.UNPROCESSABLE_ENTITY -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
