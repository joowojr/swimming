package com.swimming.backend.calendar.controller;

import com.swimming.backend.common.exception.GlobalExceptionHandler;
import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.calendar.dto.in.CreateDailyPlanItemsRequest;
import com.swimming.backend.calendar.dto.in.NewDailyPlanTask;
import com.swimming.backend.calendar.dto.in.DailyPlanItemResponse;
import com.swimming.backend.calendar.dto.in.DailyPlanItemType;
import com.swimming.backend.calendar.dto.in.DailyPlanResponse;
import com.swimming.backend.calendar.usecase.DailyPlanUseCase;
import com.swimming.backend.task.domain.TaskStatus;
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

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DailyPlanControllerTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 21);
    private DailyPlanUseCase useCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        useCase = mock(DailyPlanUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DailyPlanController(useCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthUserArgumentResolver(new AuthUser(1L, "user@example.com")))
                .build();
    }

    @Test
    @DisplayName("날짜 범위의 계획을 조회한다")
    void getsRange() throws Exception {
        when(useCase.getRange(1L, DATE, DATE)).thenReturn(List.of(planResponse()));

        mockMvc.perform(get("/api/daily-plans")
                        .queryParam("from_date", "2026-08-21")
                        .queryParam("to_date", "2026-08-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2026-08-21"))
                .andExpect(jsonPath("$[0].items[0].taskId").value(10))
                .andExpect(jsonPath("$[0].items[0].id").doesNotExist())
                .andExpect(jsonPath("$[0].items[0].itemType").value("TASK"));
    }

    @Test
    @DisplayName("날짜를 경로로 받아 폴더 없는 Task를 생성한다")
    void addsAdHocItem() throws Exception {
        CreateDailyPlanItemsRequest request = CreateDailyPlanItemsRequest.ofNewTasks(
                List.of(new NewDailyPlanTask("장보기", null)));
        when(useCase.addItems(1L, DATE, request)).thenReturn(planResponse());

        mockMvc.perform(post("/api/daily-plans/2026-08-21/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tasks\":[{\"title\":\"장보기\"}]}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/daily-plans/2026-08-21"))
                .andExpect(jsonPath("$.date").value("2026-08-21"));

        verify(useCase).addItems(1L, DATE, request);
    }

    @Test
    @DisplayName("날짜를 경로로 받아 여러 Task 항목을 일괄 생성한다")
    void addsTaskItems() throws Exception {
        CreateDailyPlanItemsRequest request = CreateDailyPlanItemsRequest.ofTaskIds(List.of(10L, 20L));
        when(useCase.addItems(1L, DATE, request)).thenReturn(planResponse());

        mockMvc.perform(post("/api/daily-plans/2026-08-21/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskIds\":[10,20]}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/daily-plans/2026-08-21"));

        verify(useCase).addItems(1L, DATE, request);
    }

    @Test
    @DisplayName("날짜와 폴더를 받아 새 Task 항목을 생성한다")
    void createsFolderTaskItem() throws Exception {
        CreateDailyPlanItemsRequest request = CreateDailyPlanItemsRequest.ofNewTasks(
                List.of(new NewDailyPlanTask("API 문서 작성", 100L)));
        when(useCase.addItems(1L, DATE, request)).thenReturn(planResponse());

        mockMvc.perform(post("/api/daily-plans/2026-08-21/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tasks\":[{\"title\":\"API 문서 작성\",\"folderId\":100}]}"))
                .andExpect(status().isCreated());

        verify(useCase).addItems(1L, DATE, request);
    }

    @Test
    @DisplayName("폴더 없이 새 Task 항목을 생성한다")
    void createsFolderlessTaskItem() throws Exception {
        CreateDailyPlanItemsRequest request = CreateDailyPlanItemsRequest.ofNewTasks(
                List.of(new NewDailyPlanTask("자격증 접수", null)));
        when(useCase.addItems(1L, DATE, request)).thenReturn(planResponse());

        mockMvc.perform(post("/api/daily-plans/2026-08-21/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tasks\":[{\"title\":\"자격증 접수\"}]}"))
                .andExpect(status().isCreated());

        verify(useCase).addItems(1L, DATE, request);
    }

    @Test
    @DisplayName("날짜별 계획에서 할 일을 뺀다")
    void removesTask() throws Exception {
        mockMvc.perform(delete("/api/daily-plans/2026-08-21/tasks/10"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(useCase).removeTask(1L, DATE, 10L);
    }

    private DailyPlanResponse planResponse() {
        return new DailyPlanResponse(DATE, List.of(new DailyPlanItemResponse(
                10L, DailyPlanItemType.TASK,
                100L, "폴더", "API 구현", TaskStatus.DOING, false, false
        )));
    }

    private record AuthUserArgumentResolver(AuthUser authUser) implements HandlerMethodArgumentResolver {
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
