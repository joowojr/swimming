package com.swimming.backend.task.controller;

import com.swimming.backend.common.exception.GlobalExceptionHandler;
import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.task.domain.TaskMatrixSection;
import com.swimming.backend.task.domain.TaskOrderingScope;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.task.dto.in.TaskMatrixPageQuery;
import com.swimming.backend.task.dto.in.TaskPlacementRequest;
import com.swimming.backend.task.dto.out.TaskMatrixItemResponse;
import com.swimming.backend.task.dto.out.TaskMatrixPageResponse;
import com.swimming.backend.task.dto.out.TaskPlacementResponse;
import com.swimming.backend.task.usecase.TaskOrderingUseCase;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskOrderingControllerTest {

    private TaskOrderingUseCase taskOrderingUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        taskOrderingUseCase = mock(TaskOrderingUseCase.class);
        AuthUser authUser = new AuthUser(1L, "user@example.com");
        TaskOrderingController controller = new TaskOrderingController(taskOrderingUseCase);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthUserArgumentResolver(authUser))
                .build();
    }

    @Test
    @DisplayName("Matrix 영역의 첫 페이지와 다음 커서를 반환한다")
    void returnsMatrixPage() throws Exception {
        TaskMatrixPageQuery query = TaskMatrixPageQuery.from("urgent", 2, null, null);
        TaskMatrixItemResponse item = new TaskMatrixItemResponse(
                2L,
                10L,
                "즉시 Task",
                TaskStatus.TODO,
                false,
                true,
                "position-cursor",
                Instant.parse("2026-09-01T10:00:00Z"),
                Instant.parse("2026-09-01T10:00:00Z")
        );
        when(taskOrderingUseCase.getMatrixPage(1L, query)).thenReturn(new TaskMatrixPageResponse(
                TaskMatrixSection.URGENT,
                List.of(item),
                "next-cursor",
                true
        ));

        mockMvc.perform(get("/api/tasks/matrix")
                        .queryParam("section", "urgent")
                        .queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.section").value("URGENT"))
                .andExpect(jsonPath("$.items[0].id").value(2))
                .andExpect(jsonPath("$.items[0].positionCursor").value("position-cursor"))
                .andExpect(jsonPath("$.nextCursor").value("next-cursor"))
                .andExpect(jsonPath("$.hasNext").value(true));

        verify(taskOrderingUseCase).getMatrixPage(1L, query);
    }

    @Test
    @DisplayName("지원하지 않는 Matrix 영역을 ProblemDetail로 거부한다")
    void rejectsInvalidMatrixSection() throws Exception {
        mockMvc.perform(get("/api/tasks/matrix").queryParam("section", "later"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_MATRIX_SECTION"));
    }

    @Test
    @DisplayName("Matrix 영역이 없으면 ProblemDetail로 거부한다")
    void rejectsMissingMatrixSection() throws Exception {
        mockMvc.perform(get("/api/tasks/matrix"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_MATRIX_SECTION"));
    }

    @Test
    @DisplayName("허용 범위를 벗어난 Matrix 페이지 크기를 ProblemDetail로 거부한다")
    void rejectsInvalidMatrixPageSize() throws Exception {
        mockMvc.perform(get("/api/tasks/matrix")
                        .queryParam("section", "standard")
                        .queryParam("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_PAGE_SIZE"));
    }

    @Test
    @DisplayName("Task를 지정한 두 이웃 사이로 이동한다")
    void movesTaskBetweenAnchors() throws Exception {
        TaskPlacementRequest request = new TaskPlacementRequest("MATRIX", "URGENT", 3L, 2L);
        TaskMatrixItemResponse item = new TaskMatrixItemResponse(
                4L,
                null,
                "이동 Task",
                TaskStatus.TODO,
                false,
                true,
                "position-cursor",
                Instant.parse("2026-09-01T10:00:00Z"),
                Instant.parse("2026-09-01T11:00:00Z")
        );
        when(taskOrderingUseCase.move(1L, 4L, request)).thenReturn(new TaskPlacementResponse(
                TaskOrderingScope.MATRIX,
                item,
                TaskMatrixSection.STANDARD,
                TaskMatrixSection.URGENT,
                List.of()
        ));

        mockMvc.perform(patch("/api/tasks/4/placement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scope":"MATRIX",
                                  "targetSection":"URGENT",
                                  "previousTaskId":3,
                                  "nextTaskId":2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("MATRIX"))
                .andExpect(jsonPath("$.task.id").value(4))
                .andExpect(jsonPath("$.task.positionCursor").value("position-cursor"))
                .andExpect(jsonPath("$.sourceSection").value("STANDARD"))
                .andExpect(jsonPath("$.targetSection").value("URGENT"));

        verify(taskOrderingUseCase).move(1L, 4L, request);
    }

    @Test
    @DisplayName("Task 정렬 범위와 대상 영역이 비어 있으면 요청을 거부한다")
    void rejectsBlankPlacementScopeAndSection() throws Exception {
        mockMvc.perform(patch("/api/tasks/4/placement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scope":" ",
                                  "targetSection":"",
                                  "previousTaskId":null,
                                  "nextTaskId":null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.scope").exists())
                .andExpect(jsonPath("$.errors.targetSection").exists());
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
