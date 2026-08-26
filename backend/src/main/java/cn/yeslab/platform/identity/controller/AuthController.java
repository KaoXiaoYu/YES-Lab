package cn.yeslab.platform.identity.controller;

import cn.yeslab.platform.common.api.ApiResponse;
import cn.yeslab.platform.identity.api.AuthModels;
import cn.yeslab.platform.identity.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/login")
    public ApiResponse<AuthModels.AuthResponse> login(@Valid @RequestBody AuthModels.LoginRequest request) {
        return ApiResponse.ok(service.login(request));
    }

    @PostMapping("/register")
    public ApiResponse<AuthModels.AuthResponse> register(@Valid @RequestBody AuthModels.RegisterRequest request) {
        return ApiResponse.ok(service.register(request));
    }

    @GetMapping("/me")
    public ApiResponse<AuthModels.AccountView> me(Authentication authentication) {
        return ApiResponse.ok(service.current(authentication));
    }
}
