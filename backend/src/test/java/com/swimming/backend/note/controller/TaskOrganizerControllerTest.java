package com.swimming.backend.note.controller;

import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.note.dto.in.TaskOrganizeRequest;
import com.swimming.backend.note.dto.in.TaskOrganizeResponse;
import com.swimming.backend.note.usecase.TaskOrganizerUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskOrganizerControllerTest {

    private TaskOrganizerUseCase taskOrganizerUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        taskOrganizerUseCase = mock(TaskOrganizerUseCase.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TaskOrganizerController(taskOrganizerUseCase))
                .setCustomArgumentResolvers(new AuthUserArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("Task Organizer Preview 응답에 confidence를 노출하지 않는다")
    void returnsPreviewWithoutConfidence() throws Exception {
        TaskOrganizeRequest request = new TaskOrganizeRequest("메모");
        when(taskOrganizerUseCase.preview(1L, request)).thenReturn(
                new TaskOrganizeResponse(
                        List.of(new TaskOrganizeResponse.TaskSuggestionResponse(
                                "원문",
                                10L,
                                "Swimming",
                                "정리된 Task"
                        )),
                        List.of()
                )
        );

        mockMvc.perform(post("/api/task-organizer/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memo":"메모"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].projectId").value(10L))
                .andExpect(jsonPath("$.suggestions[0].title").value("정리된 Task"))
                .andExpect(jsonPath("$.suggestions[0].confidence").doesNotExist());
    }

    private static class AuthUserArgumentResolver
            implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType() == AuthUser.class;
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory
        ) {
            return new AuthUser(1L, "user@example.com");
        }
    }
}
