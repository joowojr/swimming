package com.swimming.backend.plan.controller;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.common.exception.GlobalExceptionHandler;
import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.plan.dto.DailyPlanItemResponse;
import com.swimming.backend.plan.dto.DailyPlanResponse;
import com.swimming.backend.plan.dto.UpdateDailyPlanRequest;
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

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DailyPlanControllerTest {

    private DailyPlanUseCase dailyPlanUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        dailyPlanUseCase = mock(DailyPlanUseCase.class);
        DailyPlanController controller = new DailyPlanController(dailyPlanUseCase);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthUserArgumentResolver(
                        new AuthUser(1L, "user@example.com")
                ))
                .build();
    }

    @Test
    @DisplayName("시작일과 종료일 범위의 계획을 날짜순으로 반환한다")
    void returnsDailyPlansForRange() throws Exception {
        LocalDate fromDate = LocalDate.of(2026, 8, 20);
        LocalDate toDate = LocalDate.of(2026, 8, 21);
        when(dailyPlanUseCase.getRange(1L, fromDate, toDate)).thenReturn(List.of(
                new DailyPlanResponse(
                        fromDate,
                        List.of(new DailyPlanItemResponse(
                                2L,
                                10L,
                                "프로젝트",
                                "API 구현",
                                TaskStatus.DOING,
                                40,
                                0
                        ))
                ),
                new DailyPlanResponse(toDate, List.of())
        ));

        mockMvc.perform(get("/api/daily-plan")
                        .queryParam("from_date", "2026-08-20")
                        .queryParam("to_date", "2026-08-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2026-08-20"))
                .andExpect(jsonPath("$[0].items[0].taskId").value(2))
                .andExpect(jsonPath("$[0].items[0].projectName").value("프로젝트"))
                .andExpect(jsonPath("$[0].items[0].status").value("DOING"))
                .andExpect(jsonPath("$[1].date").value("2026-08-21"))
                .andExpect(jsonPath("$[1].items").isEmpty());

        verify(dailyPlanUseCase).getRange(1L, fromDate, toDate);
    }

    @Test
    @DisplayName("7일을 초과한 조회 범위는 ProblemDetail로 반환한다")
    void returnsProblemDetailForInvalidDateRange() throws Exception {
        LocalDate fromDate = LocalDate.of(2026, 8, 20);
        LocalDate toDate = LocalDate.of(2026, 8, 27);
        doThrow(new BusinessException(ErrorCode.INVALID_DAILY_PLAN_DATE_RANGE))
                .when(dailyPlanUseCase).getRange(1L, fromDate, toDate);

        mockMvc.perform(get("/api/daily-plan")
                        .queryParam("from_date", "2026-08-20")
                        .queryParam("to_date", "2026-08-27"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_DAILY_PLAN_DATE_RANGE"));
    }

    @Test
    @DisplayName("날짜별 계획을 저장하면 본문 없이 성공한다")
    void updatesDailyPlan() throws Exception {
        UpdateDailyPlanRequest request = new UpdateDailyPlanRequest(
                LocalDate.of(2026, 8, 20),
                List.of(3L, 1L, 2L)
        );

        mockMvc.perform(put("/api/daily-plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "date":"2026-08-20",
                                  "taskIds":[3,1,2]
                                }
                                """))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(dailyPlanUseCase).update(1L, request);
    }

    @Test
    @DisplayName("날짜와 Task 목록이 없으면 필드 오류를 반환한다")
    void returnsFieldErrorsForInvalidRequest() throws Exception {
        mockMvc.perform(put("/api/daily-plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "date":null,
                                  "taskIds":null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.date").exists())
                .andExpect(jsonPath("$.errors.taskIds").exists());
    }

    @Test
    @DisplayName("중복 Task 계획은 ProblemDetail로 반환한다")
    void returnsProblemDetailForDuplicateTasks() throws Exception {
        UpdateDailyPlanRequest request = new UpdateDailyPlanRequest(
                LocalDate.of(2026, 8, 20),
                List.of(1L, 1L)
        );
        doThrow(new BusinessException(ErrorCode.INVALID_DAILY_PLAN_TASKS))
                .when(dailyPlanUseCase).update(1L, request);

        mockMvc.perform(put("/api/daily-plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "date":"2026-08-20",
                                  "taskIds":[1,1]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_DAILY_PLAN_TASKS"));
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
