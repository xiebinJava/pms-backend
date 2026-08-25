package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.request.LoginRequest;
import com.brad.pms.dto.response.LoginResponse;
import com.brad.pms.dto.response.UserDTO;
import com.brad.pms.security.IgnoreAuth;
import com.brad.pms.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @IgnoreAuth
    public ResponseResult<LoginResponse> login(@Validated @RequestBody LoginRequest request) {
        return ResponseResult.success(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseResult<UserDTO> me() {
        return ResponseResult.success(authService.me());
    }
}
