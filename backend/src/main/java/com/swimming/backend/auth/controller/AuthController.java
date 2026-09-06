package com.swimming.backend.auth.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import com.swimming.backend.auth.dto.AuthResponse;
import com.swimming.backend.auth.dto.GoogleLoginRequest;
import com.swimming.backend.auth.dto.LoginResult;
import com.swimming.backend.auth.dto.RefreshResponse;
import com.swimming.backend.auth.dto.RefreshResult;
import com.swimming.backend.auth.usecase.AuthUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SecurityRequirements
@Tag(name = "인증", description = "로그인 화면(`/`). 구글 로그인과 토큰 재발급.")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthUseCase authUseCase;

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        LoginResult result = authUseCase.loginWithGoogle(request.credential());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, result.refreshCookie())
                .body(result.response());
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            @CookieValue(name = AuthUseCase.REFRESH_COOKIE_NAME, required = false) String refreshToken
    ) {
        RefreshResult result = authUseCase.refresh(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, result.refreshCookie())
                .body(result.response());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, authUseCase.logout())
                .build();
    }
}
