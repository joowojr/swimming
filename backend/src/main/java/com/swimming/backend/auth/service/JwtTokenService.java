package com.swimming.backend.auth.service;

import com.swimming.backend.auth.config.JwtProperties;
import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

@Service
public class JwtTokenService {

    private static final String ISSUER = "swimming";
    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder accessTokenDecoder;
    private final JwtDecoder refreshTokenDecoder;
    private final Duration accessTokenValidity;
    private final Duration refreshTokenValidity;

    public JwtTokenService(JwtProperties properties) {
        SecretKey secretKey = new SecretKeySpec(
                properties.secret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
        this.jwtEncoder = NimbusJwtEncoder.withSecretKey(secretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
        this.accessTokenDecoder = createDecoder(secretKey, ACCESS_TOKEN_TYPE);
        this.refreshTokenDecoder = createDecoder(secretKey, REFRESH_TOKEN_TYPE);
        this.accessTokenValidity = properties.accessTokenValidity();
        this.refreshTokenValidity = properties.refreshTokenValidity();
    }

    public TokenPair issue(Long userId, String email) {
        Instant issuedAt = Instant.now();
        return new TokenPair(
                encode(userId, email, ACCESS_TOKEN_TYPE, issuedAt, accessTokenValidity),
                encode(userId, email, REFRESH_TOKEN_TYPE, issuedAt, refreshTokenValidity)
        );
    }

    public Jwt decodeRefreshToken(String token) {
        try {
            return refreshTokenDecoder.decode(token);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN, exception);
        }
    }

    public JwtDecoder accessTokenDecoder() {
        return accessTokenDecoder;
    }

    private String encode(
            Long userId,
            String email,
            String tokenType,
            Instant issuedAt,
            Duration validity
    ) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(validity))
                .claim("email", email)
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .type("JWT")
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private JwtDecoder createDecoder(SecretKey secretKey, String tokenType) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> tokenTypeValidator = new JwtClaimValidator<>(
                TOKEN_TYPE_CLAIM,
                tokenType::equals
        );
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(ISSUER),
                tokenTypeValidator
        ));
        return decoder;
    }

    public record TokenPair(String accessToken, String refreshToken) {
    }
}
