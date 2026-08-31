package com.swimming.backend.auth.service;

import com.swimming.backend.auth.config.GoogleAuthProperties;
import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GoogleIdTokenService {

    private static final String GOOGLE_ISSUER = "https://accounts.google.com";

    private final NimbusJwtDecoder jwtDecoder;

    public GoogleIdTokenService(GoogleAuthProperties properties) {
        this.jwtDecoder = NimbusJwtDecoder
                .withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .build();
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                "aud",
                audience -> audience != null && audience.contains(properties.clientId())
        );
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(GOOGLE_ISSUER),
                audienceValidator
        ));
    }

    public GoogleIdentity verify(String credential) {
        try {
            Jwt jwt = jwtDecoder.decode(credential);
            String subject = jwt.getSubject();
            String email = jwt.getClaimAsString("email");
            Boolean emailVerified = jwt.getClaim("email_verified");
            if (subject == null || subject.isBlank() || email == null || email.isBlank() || !Boolean.TRUE.equals(emailVerified)) {
                throw new BusinessException(ErrorCode.INVALID_GOOGLE_TOKEN);
            }
            String name = jwt.getClaimAsString("name");
            return new GoogleIdentity(subject, email, name == null || name.isBlank() ? email : name);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_GOOGLE_TOKEN, exception);
        }
    }
}
