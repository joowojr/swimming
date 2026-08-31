package com.swimming.backend.auth.usecase;

import com.swimming.backend.auth.config.AuthProperties;
import com.swimming.backend.auth.config.JwtProperties;
import com.swimming.backend.auth.dto.AuthResponse;
import com.swimming.backend.auth.dto.LoginResult;
import com.swimming.backend.auth.dto.RefreshResponse;
import com.swimming.backend.auth.dto.RefreshResult;
import com.swimming.backend.auth.service.JwtTokenService;
import com.swimming.backend.auth.service.GoogleIdTokenService;
import com.swimming.backend.auth.service.GoogleIdentity;
import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.user.dto.UserAuthInfo;
import com.swimming.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AuthUseCase {

    public static final String REFRESH_COOKIE_NAME = "refreshToken";

    private final UserService userService;
    private final GoogleIdTokenService googleIdTokenService;
    private final JwtTokenService jwtTokenService;
    private final AuthProperties authProperties;
    private final JwtProperties jwtProperties;

    public LoginResult loginWithGoogle(String credential) {
        GoogleIdentity identity = googleIdTokenService.verify(credential);
        UserAuthInfo user = userService.findOrCreateGoogleUser(
                identity.subject(),
                identity.email(),
                identity.nickname()
        );
        JwtTokenService.TokenPair tokenPair = jwtTokenService.issue(user.id(), user.email());
        AuthResponse.User responseUser = toResponseUser(user);
        return new LoginResult(
                new AuthResponse(tokenPair.accessToken(), responseUser),
                createRefreshCookie(tokenPair.refreshToken())
        );
    }

    public RefreshResult refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        Jwt jwt = jwtTokenService.decodeRefreshToken(refreshToken);
        UserAuthInfo user = getRefreshUser(jwt);

        JwtTokenService.TokenPair tokenPair = jwtTokenService.issue(user.id(), user.email());
        return new RefreshResult(
                new RefreshResponse(tokenPair.accessToken()),
                createRefreshCookie(tokenPair.refreshToken())
        );
    }

    public String logout() {
        return baseRefreshCookie("")
                .maxAge(Duration.ZERO)
                .build()
                .toString();
    }

    private UserAuthInfo getRefreshUser(Jwt jwt) {
        try {
            Long userId = Long.valueOf(jwt.getSubject());
            String email = jwt.getClaimAsString("email");
            return userService.getAuthInfoById(userId)
                    .filter(user -> user.email().equalsIgnoreCase(email))
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        } catch (NumberFormatException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    private AuthResponse.User toResponseUser(UserAuthInfo user) {
        return new AuthResponse.User(
                user.id(),
                user.email(),
                user.nickname(),
                user.timezone()
        );
    }

    private String createRefreshCookie(String refreshToken) {
        return baseRefreshCookie(refreshToken)
                .maxAge(jwtProperties.refreshTokenValidity())
                .build()
                .toString();
    }

    private ResponseCookie.ResponseCookieBuilder baseRefreshCookie(String value) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(authProperties.secureCookie())
                .sameSite("Lax")
                .path("/api/auth");
    }

}
