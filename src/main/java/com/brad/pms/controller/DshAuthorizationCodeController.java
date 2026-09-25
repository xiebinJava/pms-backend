package com.brad.pms.controller;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.integration.dsh.api.DshAuthorizationCodeIssueRequest;
import com.brad.pms.integration.dsh.api.DshAuthorizationCodeIssueResponse;
import com.brad.pms.integration.dsh.service.DshAuthorizationCodeService;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Issues a one-time code using the currently authenticated PMS browser session. */
@RestController
@RequestMapping("/integration/dsh/v1/authorization-codes")
public class DshAuthorizationCodeController {

    private final DshAuthorizationCodeService authorizationCodeService;

    public DshAuthorizationCodeController(DshAuthorizationCodeService authorizationCodeService) {
        this.authorizationCodeService = authorizationCodeService;
    }

    @PostMapping
    public ResponseResult<DshAuthorizationCodeIssueResponse> issue(
            @RequestBody(required = false) DshAuthorizationCodeIssueRequest request) {
        LoginUser user = UserContext.get();
        if (user == null || user.getId() == null || user.getSessionId() == null) {
            throw BusinessException.unauthorized("PMS_DSH_SESSION_INVALID");
        }
        return ResponseResult.success(authorizationCodeService.issue(
                user.getId(), user.getSessionId(), request));
    }
}
