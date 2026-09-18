package com.brad.pms.ai.api;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.common.response.ResponseResult;

/** Stable adapter for command callers that need the PMS error envelope. */
public final class AiCommandErrorHandler {

    private AiCommandErrorHandler() {
    }

    public static ResponseResult<Void> toResponse(BusinessException exception) {
        return ResponseResult.error(exception.getCode(), exception.getMessage());
    }
}
