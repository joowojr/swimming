package com.swimming.backend.task.controller;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.common.exception.GlobalExceptionHandler;
import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.task.dto.in.CreateTaskRequest;
import com.swimming.backend.task.dto.in.DeleteTasksRequest;
import com.swimming.backend.task.dto.in.ReorderTasksRequest;
import com.swimming.backend.task.dto.in.TaskResponse;
import com.swimming.backend.task.dto.in.UpdateTaskRequest;
import com.swimming.backend.task.usecase.TaskUseCase;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskControllerTest {

    private TaskUseCase taskUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        taskUseCase = mock(TaskUseCase.class);
        AuthUser authUser = new AuthUser(1L, "user@example.com");
        TaskController taskController = new TaskController(taskUseCase);
        mockMvc = MockMvcBuilders
                .standaloneSetup(taskController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthUserArgumentResolver(authUser))
                .build();
    }

    @Test
    @DisplayName("Task 생성 시 Location 헤더와 생성 결과를 반환한다")
    void createsTaskWithLocationHeader() throws Exception {
        CreateTaskRequest request = new CreateTaskRequest("API 명세 작성");
        when(taskUseCase.create(1L, 10L, request))
                .thenReturn(response(1L, "API 명세 작성", TaskStatus.TODO, 0));

        mockMvc.perform(post("/api/projects/10/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"API 명세 작성"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/tasks/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.projectId").value(10))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.completionPct").doesNotExist())
                .andExpect(jsonPath("$.orderIdx").value(0));
    }

    @Test
    @DisplayName("프로젝트 Task 목록을 저장된 순서대로 반환한다")
    void returnsProjectTasks() throws Exception {
        when(taskUseCase.getAll(1L, 10L)).thenReturn(List.of(
                response(2L, "첫째", TaskStatus.DOING, 0),
                response(1L, "둘째", TaskStatus.TODO, 1)
        ));

        mockMvc.perform(get("/api/projects/10/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].orderIdx").value(0))
                .andExpect(jsonPath("$[1].id").value(1))
                .andExpect(jsonPath("$[1].orderIdx").value(1));
    }

    @Test
    @DisplayName("Task 제목과 상태를 수정한다")
    void updatesTask() throws Exception {
        UpdateTaskRequest request = new UpdateTaskRequest(
                "API 구현",
                TaskStatus.DOING
        );
        when(taskUseCase.update(1L, 1L, request))
                .thenReturn(response(1L, "API 구현", TaskStatus.DOING, 0));

        mockMvc.perform(patch("/api/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"API 구현",
                                  "status":"DOING"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("API 구현"))
                .andExpect(jsonPath("$.status").value("DOING"))
                .andExpect(jsonPath("$.completionPct").doesNotExist());
    }

    @Test
    @DisplayName("Task 수정 입력이 유효하지 않으면 필드 오류를 반환한다")
    void returnsFieldErrorsForInvalidUpdate() throws Exception {
        mockMvc.perform(patch("/api/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":" ",
                                  "status":null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.status").exists());
    }

    @Test
    @DisplayName("여러 Task를 삭제하면 본문 없이 성공한다")
    void deletesTasks() throws Exception {
        DeleteTasksRequest request = new DeleteTasksRequest(List.of(1L, 2L));

        mockMvc.perform(delete("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"taskIds":[1,2]}
                                """))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(taskUseCase).deleteTasks(1L, request);
    }

    @Test
    @DisplayName("삭제할 Task ID 배열이 비어 있으면 요청을 거부한다")
    void rejectsEmptyTaskDeletion() throws Exception {
        mockMvc.perform(delete("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"taskIds":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.taskIds").exists());
    }

    @Test
    @DisplayName("프로젝트의 Task 순서를 저장하면 본문 없이 성공한다")
    void reordersTasks() throws Exception {
        ReorderTasksRequest request = new ReorderTasksRequest(List.of(3L, 1L, 2L));

        mockMvc.perform(put("/api/projects/10/tasks/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"taskIds":[3,1,2]}
                                """))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(taskUseCase).reorder(1L, 10L, request);
    }

    @Test
    @DisplayName("다른 사용자의 Task는 찾을 수 없음으로 반환한다")
    void returnsNotFoundForAnotherUsersTask() throws Exception {
        when(taskUseCase.update(
                1L,
                1L,
                new UpdateTaskRequest("수정", TaskStatus.DOING)
        )).thenThrow(new BusinessException(ErrorCode.TASK_NOT_FOUND));

        mockMvc.perform(patch("/api/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"수정",
                                  "status":"DOING"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
    }

    private TaskResponse response(
            Long id,
            String title,
            TaskStatus status,
            int orderIdx
    ) {
        return new TaskResponse(
                id,
                10L,
                title,
                status,
                orderIdx,
                LocalDateTime.of(2026, 8, 20, 10, 0),
                LocalDateTime.of(2026, 8, 20, 10, 0)
        );
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
