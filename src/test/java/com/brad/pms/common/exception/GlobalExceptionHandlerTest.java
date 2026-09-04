package com.brad.pms.common.exception;

import com.brad.pms.common.response.ResponseResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void clearRequestId() {
        org.slf4j.MDC.remove("requestId");
    }

    @Test
    void mapsBusinessHttpStatusAndIncludesRequestId() {
        org.slf4j.MDC.put("requestId", "req-401");

        ResponseEntity<ResponseResult<Void>> response = handler.handleBusiness(
                new BusinessException(BusinessException.ResponseCode.UNAUTHORIZED, "未登录"));

        assertThat(response.getStatusCodeValue()).isEqualTo(401);
        assertThat(Objects.requireNonNull(response.getBody()).getCode()).isEqualTo(401);
        assertThat(response.getBody().getRequestId()).isEqualTo("req-401");
    }

    @Test
    void mapsForbiddenAndConflictWithoutChangingBusinessCode() {
        ResponseEntity<ResponseResult<Void>> forbidden = handler.handleBusiness(
                BusinessException.forbidden("无权执行此操作"));
        ResponseEntity<ResponseResult<Void>> conflict = handler.handleBusiness(
                new BusinessException(ResponseResult.CONFLICT, "资源已存在"));
        ResponseEntity<ResponseResult<Void>> notFound = handler.handleBusiness(
                BusinessException.notFound("资源不存在"));
        ResponseEntity<ResponseResult<Void>> validation = handler.handleBusiness(
                BusinessException.error("业务校验失败"));

        assertThat(forbidden.getStatusCodeValue()).isEqualTo(403);
        assertThat(conflict.getStatusCodeValue()).isEqualTo(409);
        assertThat(Objects.requireNonNull(conflict.getBody()).getCode()).isEqualTo(409);
        assertThat(notFound.getStatusCodeValue()).isEqualTo(404);
        assertThat(Objects.requireNonNull(notFound.getBody()).getCode()).isEqualTo(404);
        assertThat(validation.getStatusCodeValue()).isEqualTo(422);
        assertThat(Objects.requireNonNull(validation.getBody()).getCode()).isEqualTo(422);
    }

    @Test
    void mapsValidationToBadRequestAndUnknownErrorsToInternalServerError() {
        BindException bindException = new BindException(new Object(), "request");
        bindException.addError(new FieldError("request", "username", "用户名不能为空"));

        ResponseEntity<ResponseResult<Void>> validation = handler.handleBind(bindException);
        ResponseEntity<ResponseResult<Void>> unknown = handler.handleException(new RuntimeException("secret sql"));

        assertThat(validation.getStatusCodeValue()).isEqualTo(400);
        assertThat(unknown.getStatusCodeValue()).isEqualTo(500);
        assertThat(Objects.requireNonNull(unknown.getBody()).getMsg()).doesNotContain("secret sql");
    }
}
