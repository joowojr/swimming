package com.swimming.backend.common.config;

import com.swimming.backend.auth.service.JwtTokenService;
import com.swimming.backend.common.security.AuthUser;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.convert.converter.Converter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import com.swimming.backend.agentwork.interfaces.router.security.AgentAccessTokenFilter;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

@Configuration
public class SecurityConfig {
    private static final String AGENT_TOKEN_PREFIX = "swm_pat_";

    /**
     * API 문서는 인증 없이 연다. 문서를 보려면 토큰이 필요한 구조는 프런트가 계약을
     * 확인하는 것을 막는다. 문서에는 경로와 스키마만 담기고 데이터는 담기지 않는다.
     */
    private static final String[] API_DOCS_PATHS = {
            "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            ObjectProvider<AgentAccessTokenFilter> agentAccessTokenFilterProvider,
            ObjectMapper objectMapper
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/health", "/api/auth/**", "/api/public/**").permitAll()
                        .requestMatchers(API_DOCS_PATHS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(agentAwareBearerTokenResolver())
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint((request, response, exception) ->
                                writeProblem(
                                        response,
                                        objectMapper,
                                        HttpStatus.UNAUTHORIZED,
                                        "인증이 필요합니다",
                                        "AUTHENTICATION_REQUIRED"
                                )))
                .exceptionHandling(exception -> exception
                        .accessDeniedHandler((request, response, denied) ->
                                writeProblem(
                                        response,
                                        objectMapper,
                                        HttpStatus.FORBIDDEN,
                                        "접근 권한이 없습니다",
                                        "ACCESS_DENIED"
                                )));

        AgentAccessTokenFilter agentAccessTokenFilter = agentAccessTokenFilterProvider.getIfAvailable();
        if (agentAccessTokenFilter != null) {
            http.addFilterBefore(agentAccessTokenFilter, BearerTokenAuthenticationFilter.class);
        }
        return http.build();
    }

    private BearerTokenResolver agentAwareBearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")
                    && header.substring("Bearer ".length()).startsWith(AGENT_TOKEN_PREFIX)) {
                return null;
            }
            return delegate.resolve(request);
        };
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtTokenService jwtTokenService) {
        return jwtTokenService.accessTokenDecoder();
    }

    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return jwt -> new PreAuthenticatedAuthenticationToken(
                new AuthUser(
                        Long.valueOf(jwt.getSubject()),
                        jwt.getClaimAsString("email")
                ),
                jwt.getTokenValue(),
                List.of()
        );
    }

    private void writeProblem(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            HttpStatus status,
            String detail,
            String code
    ) throws IOException {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setProperty("code", code);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problemDetail);
    }
}
