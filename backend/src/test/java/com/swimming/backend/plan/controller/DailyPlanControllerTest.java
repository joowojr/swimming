package com.swimming.backend.plan.controller;

import com.swimming.backend.common.exception.GlobalExceptionHandler;
import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.plan.dto.CreateDailyPlanItemsRequest;
import com.swimming.backend.plan.dto.DailyPlanItemResponse;
import com.swimming.backend.plan.dto.DailyPlanResponse;
import com.swimming.backend.plan.dto.ReorderDailyPlanItemsRequest;
import com.swimming.backend.plan.dto.UpdateDailyPlanItemRequest;
import com.swimming.backend.plan.usecase.DailyPlanUseCase;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
                .andExpect(jsonPath("$[0].items[0].id").value(1));
    }

    @Test
    @DisplayName("날짜를 경로로 받아 독립 할 일을 생성한다")
    void addsAdHocItem() throws Exception {
        CreateDailyPlanItemsRequest request = new CreateDailyPlanItemsRequest(null, null, "장보기");
        when(useCase.addItems(1L, DATE, request)).thenReturn(planResponse());

        mockMvc.perform(post("/api/daily-plans/2026-08-21/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"장보기\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/daily-plans/2026-08-21"))
                .andExpect(jsonPath("$.date").value("2026-08-21"));

        verify(useCase).addItems(1L, DATE, request);
    }

    @Test
    @DisplayName("날짜를 경로로 받아 여러 Task 항목을 일괄 생성한다")
    void addsTaskItems() throws Exception {
        CreateDailyPlanItemsRequest request = new CreateDailyPlanItemsRequest(List.of(10L, 20L), null, null);
        when(useCase.addItems(1L, DATE, request)).thenReturn(planResponse());

        mockMvc.perform(post("/api/daily-plans/2026-08-21/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskIds\":[10,20]}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/daily-plans/2026-08-21"));

        verify(useCase).addItems(1L, DATE, request);
    }

    @Test
    @DisplayName("날짜와 프로젝트를 받아 새 Task 항목을 생성한다")
    void createsProjectTaskItem() throws Exception {
        CreateDailyPlanItemsRequest request = new CreateDailyPlanItemsRequest(null, 100L, "API 문서 작성");
        when(useCase.addItems(1L, DATE, request)).thenReturn(planResponse());

        mockMvc.perform(post("/api/daily-plans/2026-08-21/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":100,\"title\":\"API 문서 작성\"}"))
                .andExpect(status().isCreated());

        verify(useCase).addItems(1L, DATE, request);
    }

    @Test
    @DisplayName("날짜별 계획의 항목 순서를 저장한다")
    void reordersItems() throws Exception {
        ReorderDailyPlanItemsRequest request = new ReorderDailyPlanItemsRequest(List.of(2L, 1L));
        when(useCase.reorder(1L, DATE, request)).thenReturn(planResponse());

        mockMvc.perform(put("/api/daily-plans/2026-08-21")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemIds\":[2,1]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    @DisplayName("독립 할 일 제목을 수정한다")
    void updatesItem() throws Exception {
        UpdateDailyPlanItemRequest request = new UpdateDailyPlanItemRequest("책 반납");
        when(useCase.updateItem(1L, DATE, 2L, request)).thenReturn(planResponse());

        mockMvc.perform(patch("/api/daily-plans/2026-08-21/items/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"책 반납\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("날짜별 계획에서 항목을 제거한다")
    void deletesItem() throws Exception {
        mockMvc.perform(delete("/api/daily-plans/2026-08-21/items/2"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(useCase).deleteItem(1L, DATE, 2L);
    }

    @Test
    @DisplayName("빈 독립 할 일 제목은 필드 오류로 반환한다")
    void rejectsBlankTitle() throws Exception {
        mockMvc.perform(patch("/api/daily-plans/2026-08-21/items/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.title").exists());
    }

    private DailyPlanResponse planResponse() {
        return new DailyPlanResponse(DATE, List.of(new DailyPlanItemResponse(
                1L, 10L, 100L, "프로젝트", "API 구현", TaskStatus.DOING, 0
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
