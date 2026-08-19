package com.swimming.backend.auth.controller;

import com.swimming.backend.auth.dto.AuthResponse;
import com.swimming.backend.auth.dto.LoginRequest;
import com.swimming.backend.auth.dto.LoginResult;
import com.swimming.backend.auth.dto.RefreshResponse;
import com.swimming.backend.auth.dto.RefreshResult;
import com.swimming.backend.auth.usecase.AuthUseCase;
import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.common.exception.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private AuthUseCase authUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authUseCase = mock(AuthUseCase.class);
        AuthController authController = new AuthController(authUseCase);
        mockMvc = MockMvcBuilders
                .standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsTokensAndUserForValidLogin() throws Exception {
        LoginRequest request = new LoginRequest("joowojr@gmail.com", "1234");
        AuthResponse response = new AuthResponse(
                "access-token",
                new AuthResponse.User(1L, "joowojr@gmail.com", "joowojr", "Asia/Seoul")
        );
        when(authUseCase.login(request)).thenReturn(new LoginResult(
                response,
                "refreshToken=refresh-token; Path=/api/auth; HttpOnly; SameSite=Lax"
        ));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"joowojr@gmail.com","password":"1234"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.user.id").value(1))
                .andExpect(jsonPath("$.user.email").value("joowojr@gmail.com"))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString("refreshToken=refresh-token")
                ))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));
    }

    @Test
    void returnsFieldErrorsForInvalidInput() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"invalid","password":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void returnsProblemDetailForInvalidCredentials() throws Exception {
        LoginRequest request = new LoginRequest("joowojr@gmail.com", "wrong");
        when(authUseCase.login(request))
                .thenThrow(new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"joowojr@gmail.com","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void rotatesRefreshCookie() throws Exception {
        when(authUseCase.refresh("old-refresh-token")).thenReturn(new RefreshResult(
                new RefreshResponse("new-access-token"),
                "refreshToken=new-refresh-token; Path=/api/auth; HttpOnly; SameSite=Lax"
        ));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refreshToken", "old-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString("refreshToken=new-refresh-token")
                ));
    }

    @Test
    void clearsRefreshCookieOnLogout() throws Exception {
        when(authUseCase.logout())
                .thenReturn("refreshToken=; Path=/api/auth; Max-Age=0; HttpOnly; SameSite=Lax");

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
    }
}
