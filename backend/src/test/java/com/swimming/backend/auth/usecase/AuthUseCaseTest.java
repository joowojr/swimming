package com.swimming.backend.auth.usecase;

import com.swimming.backend.auth.config.AuthProperties;
import com.swimming.backend.auth.config.JwtProperties;
import com.swimming.backend.auth.dto.LoginResult;
import com.swimming.backend.auth.service.GoogleIdTokenService;
import com.swimming.backend.auth.service.GoogleIdentity;
import com.swimming.backend.auth.service.JwtTokenService;
import com.swimming.backend.user.dto.UserAuthInfo;
import com.swimming.backend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthUseCaseTest {

    @Mock
    private UserService userService;
    @Mock
    private GoogleIdTokenService googleIdTokenService;
    @Mock
    private JwtTokenService jwtTokenService;

    private AuthUseCase authUseCase;

    @BeforeEach
    void setUp() {
        authUseCase = new AuthUseCase(
                userService,
                googleIdTokenService,
                jwtTokenService,
                new AuthProperties(true),
                new JwtProperties("test-secret-key-that-is-at-least-32-characters", Duration.ofHours(1), Duration.ofDays(14))
        );
    }

    @Test
    void GoogleID토큰을검증한뒤앱토큰을발급한다() {
        GoogleIdentity identity = new GoogleIdentity("google-subject", "user@example.com", "사용자");
        UserAuthInfo user = new UserAuthInfo(1L, "user@example.com", "사용자", "Asia/Seoul");
        when(googleIdTokenService.verify("google-id-token")).thenReturn(identity);
        when(userService.findOrCreateGoogleUser("google-subject", "user@example.com", "사용자")).thenReturn(user);
        when(jwtTokenService.issue(1L, "user@example.com"))
                .thenReturn(new JwtTokenService.TokenPair("access-token", "refresh-token"));

        LoginResult result = authUseCase.loginWithGoogle("google-id-token");

        assertThat(result.response().accessToken()).isEqualTo("access-token");
        assertThat(result.response().user().email()).isEqualTo("user@example.com");
        assertThat(result.refreshCookie()).contains("refreshToken=refresh-token");
    }
}
