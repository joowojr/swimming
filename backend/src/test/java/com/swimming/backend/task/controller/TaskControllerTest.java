package com.swimming.backend.task.controller;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.common.exception.GlobalExceptionHandler;
import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.task.dto.in.CreateTaskWithPlanRequest;
import com.swimming.backend.task.dto.in.CreateTasksBatchRequest;
import com.swimming.backend.task.dto.in.DeleteTasksRequest;
import com.swimming.backend.common.dto.CursorPage;
import com.swimming.backend.task.dto.in.TaskSummaryResponse;
import com.swimming.backend.task.dto.in.TaskResponse;
import com.swimming.backend.task.dto.in.TaskSort;
import com.swimming.backend.calendar.dto.in.DailyPlanResponse;
import com.swimming.backend.task.dto.in.UpdateTaskInfoRequest;
import com.swimming.backend.task.dto.in.UpdateTaskInfoResponse;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        CreateTaskWithPlanRequest request = new CreateTaskWithPlanRequest(
                "API 명세 작성", 10L, false, false, null);
        when(taskUseCase.createWithOptionalPlan(1L, request))
                .thenReturn(response(1L, "API 명세 작성", TaskStatus.TODO, 0));

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"API 명세 작성","folderId":10}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/tasks/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.folderId").value(10))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.completionPct").doesNotExist())
                .andExpect(jsonPath("$.orderIdx").value(0));
    }

    @Test
    @DisplayName("담은 Task를 한 번에 만들고 201과 목록을 돌려준다")
    void createsTasksInBatch() throws Exception {
        when(taskUseCase.createBatch(eq(1L), any(CreateTasksBatchRequest.class))).thenReturn(List.of(
                response(1L, "알고리즘", TaskStatus.TODO, 0),
                response(2L, "CS 정리", TaskStatus.TODO, 1)
        ));

        mockMvc.perform(post("/api/tasks/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tasks":[{"title":"알고리즘","folderId":10},{"title":"CS 정리"}]}
                                """))
                .andExpect(status().isCreated())
                // 만들어진 자원이 여럿이라 가리킬 URI가 하나가 아니다.
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("알고리즘"));
    }

    @Test
    @DisplayName("빈 배열은 거부한다")
    void rejectsEmptyBatch() throws Exception {
        mockMvc.perform(post("/api/tasks/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tasks":[]}
                                """))
                .andExpect(status().isBadRequest());

        verify(taskUseCase, never()).createBatch(any(), any());
    }

    @Test
    @DisplayName("상한을 넘긴 요청은 거부한다")
    void rejectsOversizedBatch() throws Exception {
        String tasks = String.join(",", java.util.Collections.nCopies(
                CreateTasksBatchRequest.MAX_SIZE + 1, "{\"title\":\"할 일\"}"));

        mockMvc.perform(post("/api/tasks/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tasks\":[" + tasks + "]}"))
                .andExpect(status().isBadRequest());

        verify(taskUseCase, never()).createBatch(any(), any());
    }

    @Test
    @DisplayName("제목이 빈 항목이 섞이면 거부한다")
    void rejectsBlankTitleInBatch() throws Exception {
        mockMvc.perform(post("/api/tasks/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tasks":[{"title":"알고리즘"},{"title":"   "}]}
                                """))
                .andExpect(status().isBadRequest());

        verify(taskUseCase, never()).createBatch(any(), any());
    }

    @Test
    @DisplayName("정렬 없이 조회하면 최신순으로 반환한다")
    void returnsAllOwnedTasks() throws Exception {
        when(taskUseCase.getList(1L, TaskSort.DESC)).thenReturn(List.of(
                response(2L, "최근 Task", TaskStatus.DOING, 1),
                response(1L, "이전 Task", TaskStatus.TODO, 0)
        ));

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[1].id").value(1));

        verify(taskUseCase).getList(1L, TaskSort.DESC);
    }

    @Test
    @DisplayName("sort=asc면 오래된순으로 조회한다")
    void returnsTasksInAscendingOrder() throws Exception {
        when(taskUseCase.getList(1L, TaskSort.ASC)).thenReturn(List.of(
                response(1L, "이전 Task", TaskStatus.TODO, 0)
        ));

        mockMvc.perform(get("/api/tasks").queryParam("sort", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));

        verify(taskUseCase).getList(1L, TaskSort.ASC);
    }

    @Test
    @DisplayName("지원하지 않는 정렬 조건은 ProblemDetail로 거부한다")
    void rejectsUnsupportedTaskSort() throws Exception {
        mockMvc.perform(get("/api/tasks").queryParam("sort", "newest"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_TASK_SORT"));
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
    @DisplayName("수정하기는 Task와 바뀐 날짜의 계획을 함께 반환한다")
    void updatesTaskInfo() throws Exception {
        LocalDate date = LocalDate.of(2026, 9, 5);
        UpdateTaskInfoRequest request = new UpdateTaskInfoRequest(
                "API 구현", 10L, true, false, new UpdateTaskInfoRequest.PlanMove(7L, date));
        when(taskUseCase.updateInfo(1L, 1L, request)).thenReturn(new UpdateTaskInfoResponse(
                response(1L, "API 구현", TaskStatus.TODO, 0),
                List.of(new DailyPlanResponse(date, List.of()))
        ));

        mockMvc.perform(patch("/api/tasks/1/info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"API 구현","folderId":10,"priority":true,"urgent":false,"plan":{"itemId":7,"date":"2026-09-05"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.title").value("API 구현"))
                .andExpect(jsonPath("$.plans[0].date").value("2026-09-05"));
    }

    @Test
    @DisplayName("수정하기에 중요 여부가 없으면 필드 오류를 반환한다")
    void returnsFieldErrorWhenPriorityMissing() throws Exception {
        mockMvc.perform(patch("/api/tasks/1/info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"API 구현","folderId":null,"urgent":false}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
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

    @Test
    @DisplayName("폴더의 할 일 목록을 커서 페이지로 반환한다")
    void returnsFolderTaskPage() throws Exception {
        when(taskUseCase.getPageByFolder(1L, 10L, 20, null)).thenReturn(new CursorPage<>(
                List.of(
                        new TaskSummaryResponse(2L, "첫째", TaskStatus.DOING, 0),
                        new TaskSummaryResponse(1L, "둘째", TaskStatus.TODO, 1)
                ),
                "cursor-abc",
                true
        ));

        mockMvc.perform(get("/api/folders/10/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(2))
                .andExpect(jsonPath("$.items[0].orderIdx").value(0))
                .andExpect(jsonPath("$.items[1].id").value(1))
                .andExpect(jsonPath("$.nextCursor").value("cursor-abc"))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    @DisplayName("폴더 할 일 목록은 size와 cursor를 그대로 전달한다")
    void passesFolderTaskPageQuery() throws Exception {
        when(taskUseCase.getPageByFolder(1L, 10L, 5, "cursor-abc"))
                .thenReturn(new CursorPage<>(List.of(), null, false));

        mockMvc.perform(get("/api/folders/10/tasks")
                        .param("size", "5")
                        .param("cursor", "cursor-abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false));

        verify(taskUseCase).getPageByFolder(1L, 10L, 5, "cursor-abc");
    }

    @Test
    @DisplayName("허용 범위를 넘는 size는 400으로 거부한다")
    void rejectsTooLargeSize() throws Exception {
        mockMvc.perform(get("/api/folders/10/tasks").param("size", "500"))
                .andExpect(status().isBadRequest());
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
                Instant.parse("2026-08-20T10:00:00Z"),
                Instant.parse("2026-08-20T10:00:00Z")
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
