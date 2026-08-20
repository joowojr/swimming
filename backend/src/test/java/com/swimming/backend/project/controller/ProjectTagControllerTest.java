package com.swimming.backend.project.controller;

import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.project.dto.ProjectTagResponse;
import com.swimming.backend.project.usecase.ProjectTagUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    @DisplayName("프로젝트 태그 독립 생성 엔드포인트를 제공하지 않는다")
    void doesNotExposeStandaloneProjectTagCreation() throws Exception {
        mockMvc.perform(post("/api/project-tags"))
                .andExpect(status().isMethodNotAllowed());
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
