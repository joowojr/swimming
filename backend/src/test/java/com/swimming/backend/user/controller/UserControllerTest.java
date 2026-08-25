package com.swimming.backend.user.controller;

import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.common.exception.GlobalExceptionHandler;
import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserControllerTest {

    @Mock
    private UserService userService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthUserArgumentResolver(new AuthUser(1L, "user@example.com")))
                .build();
    }

    @Test
    @DisplayName("인증된 사용자가 비밀번호를 변경한다")
    void changesPassword() throws Exception {
        mockMvc.perform(patch("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"old-password","newPassword":"new-password-1234"}
                                """))
                .andExpect(status().isNoContent());

        verify(userService).changePassword(1L, "old-password", "new-password-1234");
    }

    @Test
    @DisplayName("비밀번호 변경 입력이 유효하지 않으면 필드 오류를 반환한다")
    void validatesPasswordRequest() throws Exception {
        mockMvc.perform(patch("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"\",\"newPassword\":\"abcdefghij\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currentPassword").exists())
                .andExpect(jsonPath("$.errors.newPassword").exists());
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 변경할 수 없다")
    void rejectsWrongCurrentPassword() throws Exception {
        doThrow(new com.swimming.backend.common.exception.BusinessException(ErrorCode.CURRENT_PASSWORD_MISMATCH))
                .when(userService).changePassword(eq(1L), eq("wrong-password"), eq("new-password-1234"));

        mockMvc.perform(patch("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"wrong-password","newPassword":"new-password-1234"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_MISMATCH"));
    }

    private record AuthUserArgumentResolver(AuthUser authUser)
            implements org.springframework.web.method.support.HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(org.springframework.core.MethodParameter parameter) {
            return parameter.getParameterType() == AuthUser.class;
        }

        @Override
        public Object resolveArgument(
                org.springframework.core.MethodParameter parameter,
                org.springframework.web.method.support.ModelAndViewContainer mavContainer,
                org.springframework.web.context.request.NativeWebRequest webRequest,
                org.springframework.web.bind.support.WebDataBinderFactory binderFactory
        ) {
            return authUser;
        }
    }
}
