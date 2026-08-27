package com.swimming.backend.task.controller;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.common.exception.GlobalExceptionHandler;
import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.task.dto.in.CreateTaskRequest;
import com.swimming.backend.task.dto.in.DeleteTasksRequest;
import com.swimming.backend.task.dto.in.TaskResponse;
import com.swimming.backend.task.dto.in.TaskListMode;
import com.swimming.backend.task.dto.in.UpdateTaskStatusRequest;
import com.swimming.backend.task.dto.in.UpdateTaskTitleRequest;
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
    @DisplayName("폴더 Task 목록을 저장된 순서대로 반환한다")
    void returnsProjectTasks() throws Exception {
        when(taskUseCase.getByProject(1L, 10L)).thenReturn(List.of(
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
    @DisplayName("전체 모드로 사용자의 모든 Task를 조회한다")
    void returnsAllOwnedTasks() throws Exception {
        when(taskUseCase.getList(1L, TaskListMode.ALL)).thenReturn(List.of(
                response(2L, "최근 Task", TaskStatus.DOING, 1),
                response(1L, "이전 Task", TaskStatus.TODO, 0)
        ));

        mockMvc.perform(get("/api/tasks").queryParam("mode", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[1].id").value(1));

        verify(taskUseCase).getList(1L, TaskListMode.ALL);
    }

    @Test
    @DisplayName("미분류 모드로 폴더 없는 Task를 조회한다")
    void returnsUnclassifiedTasks() throws Exception {
        TaskResponse unclassified = new TaskResponse(
                2L,
                null,
                "미분류 Task",
                TaskStatus.TODO,
                0,
                LocalDateTime.of(2026, 8, 20, 10, 0),
                LocalDateTime.of(2026, 8, 20, 10, 0)
        );
        when(taskUseCase.getList(1L, TaskListMode.UNCLASSIFIED))
                .thenReturn(List.of(unclassified));

        mockMvc.perform(get("/api/tasks").queryParam("mode", "unclassified"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].projectId").doesNotExist());

        verify(taskUseCase).getList(1L, TaskListMode.UNCLASSIFIED);
    }

    @Test
    @DisplayName("지원하지 않는 Task 목록 모드는 ProblemDetail로 거부한다")
    void rejectsUnsupportedTaskListMode() throws Exception {
        mockMvc.perform(get("/api/tasks").queryParam("mode", "project"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_TASK_LIST_MODE"));
    }

    @Test
    @DisplayName("Task 목록 모드가 없으면 ProblemDetail로 거부한다")
    void rejectsMissingTaskListMode() throws Exception {
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_TASK_LIST_MODE"));
    }

    @Test
    @DisplayName("Task 제목만 수정한다")
    void updatesTaskTitle() throws Exception {
        UpdateTaskTitleRequest request = new UpdateTaskTitleRequest("API 구현");
        when(taskUseCase.updateTitle(1L, 1L, request))
                .thenReturn(response(1L, "API 구현", TaskStatus.TODO, 0));

        mockMvc.perform(patch("/api/tasks/1/title")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"API 구현"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("API 구현"))
                .andExpect(jsonPath("$.completionPct").doesNotExist());
    }

    @Test
    @DisplayName("Task 상태만 수정한다")
    void updatesTaskStatus() throws Exception {
        UpdateTaskStatusRequest request = new UpdateTaskStatusRequest(TaskStatus.DOING);
        when(taskUseCase.updateStatus(1L, 1L, request))
                .thenReturn(response(1L, "API 구현", TaskStatus.DOING, 0));

        mockMvc.perform(patch("/api/tasks/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DOING"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("API 구현"))
                .andExpect(jsonPath("$.status").value("DOING"));
    }

    @Test
    @DisplayName("빈 제목으로 수정하면 필드 오류를 반환한다")
    void returnsFieldErrorForBlankTitle() throws Exception {
        mockMvc.perform(patch("/api/tasks/1/title")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.title").exists());
    }

    @Test
    @DisplayName("상태 없이 상태 수정을 요청하면 필드 오류를 반환한다")
    void returnsFieldErrorForMissingStatus() throws Exception {
        mockMvc.perform(patch("/api/tasks/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
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
    @DisplayName("다른 사용자의 Task는 찾을 수 없음으로 반환한다")
    void returnsNotFoundForAnotherUsersTask() throws Exception {
        when(taskUseCase.updateTitle(
                1L,
                1L,
                new UpdateTaskTitleRequest("수정")
        )).thenThrow(new BusinessException(ErrorCode.TASK_NOT_FOUND));

        mockMvc.perform(patch("/api/tasks/1/title")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"수정"}
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
