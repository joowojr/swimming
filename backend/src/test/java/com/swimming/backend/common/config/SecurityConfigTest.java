package com.swimming.backend.common.config;

import com.swimming.backend.auth.config.JwtProperties;
import com.swimming.backend.auth.service.JwtTokenService;
import com.swimming.backend.common.security.AuthUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SecurityConfigTest.TestController.class)
@Import({
        SecurityConfig.class,
        JwtTokenService.class,
        SecurityConfigTest.TestController.class
})
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-that-is-at-least-32-characters",
        "jwt.access-token-validity=1h",
        "jwt.refresh-token-validity=14d",
        "app.cors.allowed-origins[0]=http://localhost:5173"
})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Test
    @DisplayName("인증 API는 토큰 없이 접근할 수 있다")
    void allowsPublicAuthenticationEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/api/auth/test-public"))
                .andExpect(status().isOk())
                .andExpect(content().string("public"));
    }

    @Test
    @DisplayName("보호 API에 토큰 없이 접근하면 ProblemDetail을 반환한다")
    void rejectsProtectedEndpointWithoutTokenAsProblemDetail() throws Exception {
        mockMvc.perform(get("/api/test-protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("유효한 JWT로 보호 API에 접근할 수 있다")
    void allowsProtectedEndpointWithJwt() throws Exception {
        mockMvc.perform(get("/api/test-protected").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(content().string("protected"));
    }

    @Test
    @DisplayName("검증된 JWT를 서비스용 AuthUser Principal로 변환한다")
    void convertsValidatedJwtToAuthUserPrincipal() throws Exception {
        String accessToken = jwtTokenService
                .issue(7L, "user@example.com")
                .accessToken();

        mockMvc.perform(get("/api/test-auth-user")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.email").value("user@example.com"));
    }

    @RestController
    static class TestController {

        @GetMapping("/api/auth/test-public")
        String publicEndpoint() {
            return "public";
        }

        @GetMapping("/api/test-protected")
        String protectedEndpoint() {
            return "protected";
        }

        @GetMapping("/api/test-auth-user")
        AuthUser authUser(@AuthenticationPrincipal AuthUser authUser) {
            return authUser;
        }
    }
}
