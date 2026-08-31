package com.swimming.backend.project.controller;

import com.swimming.backend.common.exception.GlobalExceptionHandler;
import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.project.dto.ProjectTagNameRequest;
import com.swimming.backend.project.dto.ProjectTagResponse;
import com.swimming.backend.project.usecase.ProjectTagUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectTagControllerTest {

    private ProjectTagUseCase projectTagUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        projectTagUseCase = mock(ProjectTagUseCase.class);
        AuthUser authUser = new AuthUser(1L, "user@example.com");
        ProjectTagController controller = new ProjectTagController(projectTagUseCase);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthUserArgumentResolver(authUser))
                .build();
    }

    @Test
    @DisplayName("인증 사용자의 프로젝트 태그 목록을 반환한다")
    void returnsCurrentUsersProjectTags() throws Exception {
        when(projectTagUseCase.getAll(1L)).thenReturn(List.of(
                new ProjectTagResponse(2L, "사이드 프로젝트"),
                new ProjectTagResponse(3L, "취준")
        ));

        mockMvc.perform(get("/api/project-tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("사이드 프로젝트"))
                .andExpect(jsonPath("$[1].name").value("취준"));
    }

    @Test
    @DisplayName("프로젝트 태그를 생성하고 Location 헤더를 반환한다")
    void createsProjectTag() throws Exception {
        ProjectTagNameRequest request = new ProjectTagNameRequest("취준");
        when(projectTagUseCase.create(1L, request))
                .thenReturn(new ProjectTagResponse(3L, "취준"));

        mockMvc.perform(post("/api/project-tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"취준"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/project-tags/3"))
                .andExpect(jsonPath("$.id").value(3L))
                .andExpect(jsonPath("$.name").value("취준"));
    }

    @Test
    @DisplayName("프로젝트 태그 이름을 수정한다")
    void updatesProjectTag() throws Exception {
        ProjectTagNameRequest request = new ProjectTagNameRequest("이직");
        when(projectTagUseCase.updateName(1L, 3L, request))
                .thenReturn(new ProjectTagResponse(3L, "이직"));

        mockMvc.perform(patch("/api/project-tags/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"이직"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("이직"));
    }

    @Test
    @DisplayName("프로젝트 태그를 삭제하면 본문 없이 성공한다")
    void deletesProjectTag() throws Exception {
        mockMvc.perform(delete("/api/project-tags/3"))
                .andExpect(status().isNoContent());

        verify(projectTagUseCase).delete(1L, 3L);
    }

    @Test
    @DisplayName("프로젝트 태그 이름이 비어 있거나 30자를 초과하면 필드 오류를 반환한다")
    void rejectsInvalidProjectTagName() throws Exception {
        mockMvc.perform(post("/api/project-tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("태그 이름을 입력해 주세요"));

        mockMvc.perform(patch("/api/project-tags/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"1234567890123456789012345678901"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("태그 이름은 30자 이하여야 합니다"));
    }

    private record AuthUserArgumentResolver(AuthUser authUser)
            implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType() == AuthUser.class
                    && parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory
        ) {
            return authUser;
        }
    }
}
