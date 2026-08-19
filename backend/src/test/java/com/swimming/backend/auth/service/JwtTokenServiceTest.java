package com.swimming.backend.auth.service;

import com.swimming.backend.auth.config.JwtProperties;
import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService(
                new JwtProperties(
                        "test-secret-key-that-is-at-least-32-characters",
                        Duration.ofHours(1),
                        Duration.ofDays(14)
                )
        );
    }

    @Test
    void issuesAndDecodesPurposeBoundTokens() {
        JwtTokenService.TokenPair tokenPair = jwtTokenService.issue(
                1L,
                "joowojr@gmail.com"
        );

        Jwt accessToken = jwtTokenService.accessTokenDecoder().decode(tokenPair.accessToken());
        Jwt refreshToken = jwtTokenService.decodeRefreshToken(tokenPair.refreshToken());

        assertThat(accessToken.getSubject()).isEqualTo("1");
        assertThat(accessToken.getClaimAsString("email")).isEqualTo("joowojr@gmail.com");
        assertThat(accessToken.getClaimAsString("token_type")).isEqualTo("access");
        assertThat(refreshToken.getClaimAsString("token_type")).isEqualTo("refresh");
    }

    @Test
    void rejectsAccessTokenAsRefreshToken() {
        JwtTokenService.TokenPair tokenPair = jwtTokenService.issue(
                1L,
                "joowojr@gmail.com"
        );

        assertThatThrownBy(() -> jwtTokenService.decodeRefreshToken(tokenPair.accessToken()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Test
    void rejectsRefreshTokenAsAccessToken() {
        JwtTokenService.TokenPair tokenPair = jwtTokenService.issue(
                1L,
                "joowojr@gmail.com"
        );

        assertThatThrownBy(() ->
                jwtTokenService.accessTokenDecoder().decode(tokenPair.refreshToken()))
                .isInstanceOf(JwtException.class);
    }
}
