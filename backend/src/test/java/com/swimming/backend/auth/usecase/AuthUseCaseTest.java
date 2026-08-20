package com.swimming.backend.auth.usecase;

import com.swimming.backend.auth.config.AuthProperties;
import com.swimming.backend.auth.config.JwtProperties;
import com.swimming.backend.auth.dto.LoginRequest;
import com.swimming.backend.auth.dto.AuthResponse;
import com.swimming.backend.auth.dto.LoginResult;
import com.swimming.backend.auth.dto.RefreshResponse;
import com.swimming.backend.auth.dto.RefreshResult;
import com.swimming.backend.auth.service.JwtTokenService;
import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.user.dto.UserAuthInfo;
import com.swimming.backend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthUseCaseTest {

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private UserService userService;

    private PasswordEncoder passwordEncoder;
    private AuthUseCase authUseCase;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authUseCase = new AuthUseCase(
                userService,
                passwordEncoder,
                jwtTokenService,
                new AuthProperties(true),
                new JwtProperties(
                        "test-secret-key-that-is-at-least-32-characters",
                        Duration.ofHours(1),
                        Duration.ofDays(14)
                )
        );
    }

    @Test
    void logsInStoredAccount() {
        UserAuthInfo user = user(1L, "joowojr@gmail.com", "1234", "joowojr", "Asia/Seoul");
        when(userService.getAuthInfoByEmail("joowojr@gmail.com"))
                .thenReturn(Optional.of(user));
        when(jwtTokenService.issue(1L, "joowojr@gmail.com"))
                .thenReturn(new JwtTokenService.TokenPair("access-token", "refresh-token"));

        LoginResult result = authUseCase.login(
                new LoginRequest("joowojr@gmail.com", "1234")
        );

        assertThat(result.response().accessToken()).isEqualTo("access-token");
        assertThat(result.response().user().id()).isEqualTo(1L);
        assertThat(result.response().user().email()).isEqualTo("joowojr@gmail.com");
        assertThat(result.response().user().nickname()).isEqualTo("joowojr");
        assertThat(result.response().user().timezone()).isEqualTo("Asia/Seoul");
        assertThat(result.refreshCookie())
                .contains("refreshToken=refresh-token")
                .contains("Path=/api/auth")
                .contains("Max-Age=1209600")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax");
    }

    @Test
    void rejectsUnknownEmail() {
        when(userService.getAuthInfoByEmail("other@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authUseCase.login(
                new LoginRequest("other@example.com", "1234")
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    void rejectsWrongPassword() {
        UserAuthInfo user = user(1L, "joowojr@gmail.com", "1234", "joowojr", "Asia/Seoul");
        when(userService.getAuthInfoByEmail("joowojr@gmail.com"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authUseCase.login(
                new LoginRequest("joowojr@gmail.com", "wrong")
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    void refreshesTokensForStoredAccount() {
        UserAuthInfo user = user(1L, "joowojr@gmail.com", "1234", "joowojr", "Asia/Seoul");
        Jwt refreshJwt = org.mockito.Mockito.mock(Jwt.class);
        when(refreshJwt.getSubject()).thenReturn("1");
        when(refreshJwt.getClaimAsString("email")).thenReturn("joowojr@gmail.com");
        when(jwtTokenService.decodeRefreshToken("old-refresh-token")).thenReturn(refreshJwt);
        when(userService.getAuthInfoById(1L)).thenReturn(Optional.of(user));
        when(jwtTokenService.issue(1L, "joowojr@gmail.com"))
                .thenReturn(new JwtTokenService.TokenPair("new-access-token", "new-refresh-token"));

        RefreshResult result = authUseCase.refresh("old-refresh-token");

        assertThat(result.response().accessToken()).isEqualTo("new-access-token");
        assertThat(result.refreshCookie())
                .contains("refreshToken=new-refresh-token")
                .contains("Max-Age=1209600");
        verify(jwtTokenService).decodeRefreshToken("old-refresh-token");
    }

    @Test
    void createsExpiredRefreshCookieForLogout() {
        String expiredRefreshCookie = authUseCase.logout();

        assertThat(expiredRefreshCookie)
                .contains("refreshToken=")
                .contains("Path=/api/auth")
                .contains("Max-Age=0")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax");
    }

    @Test
    void rejectsMissingRefreshToken() {
        assertThatThrownBy(() -> authUseCase.refresh(null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Test
    void rejectsRefreshTokenForMissingUser() {
        Jwt refreshJwt = org.mockito.Mockito.mock(Jwt.class);
        when(refreshJwt.getSubject()).thenReturn("99");
        when(refreshJwt.getClaimAsString("email")).thenReturn("deleted@example.com");
        when(jwtTokenService.decodeRefreshToken("refresh-token")).thenReturn(refreshJwt);
        when(userService.getAuthInfoById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authUseCase.refresh("refresh-token"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
    }

    private UserAuthInfo user(
            Long id,
            String email,
            String password,
            String nickname,
            String timezone
    ) {
        return new UserAuthInfo(
                id,
                email,
                passwordEncoder.encode(password),
                nickname,
                timezone
        );
    }
}
