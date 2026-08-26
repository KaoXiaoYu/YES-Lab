package cn.yeslab.platform.identity.controller;

import cn.yeslab.platform.common.api.ApiResponse;
import cn.yeslab.platform.common.error.ApiException;
import cn.yeslab.platform.identity.api.AuthModels;
import cn.yeslab.platform.identity.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    static final String REFRESH_COOKIE = "yeslab_refresh_token";

    private final AuthService service;
    private final boolean secureCookie;

    public AuthController(
            AuthService service,
            @Value("${yeslab.security.refresh-token.secure-cookie}") boolean secureCookie
    ) {
        this.service = service;
        this.secureCookie = secureCookie;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthModels.AuthResponse>> login(
            @Valid @RequestBody AuthModels.LoginRequest request
    ) {
        return sessionResponse(service.login(request));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthModels.AuthResponse>> register(
            @Valid @RequestBody AuthModels.RegisterRequest request
    ) {
        return sessionResponse(service.register(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthModels.AuthResponse>> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "没有可恢复的登录状态");
        }
        try {
            return sessionResponse(service.refresh(refreshToken));
        } catch (ApiException exception) {
            response.addHeader(HttpHeaders.SET_COOKIE, clearCookie().toString());
            throw exception;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken
    ) {
        service.logout(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie().toString())
                .body(ApiResponse.ok(null));
    }

    @GetMapping("/me")
    public ApiResponse<AuthModels.AccountView> me(Authentication authentication) {
        return ApiResponse.ok(service.current(authentication));
    }

    private ResponseEntity<ApiResponse<AuthModels.AuthResponse>> sessionResponse(
            AuthService.AuthSession session
    ) {
        ResponseCookie.ResponseCookieBuilder cookie = ResponseCookie.from(REFRESH_COOKIE, session.refreshToken())
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(session.rememberLogin()
                        ? session.refreshTtl()
                        : Duration.ofSeconds(-1));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.build().toString())
                .body(ApiResponse.ok(session.response()));
    }

    private ResponseCookie clearCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(Duration.ZERO)
                .build();
    }
}
