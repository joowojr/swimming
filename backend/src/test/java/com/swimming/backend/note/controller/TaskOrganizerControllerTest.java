package com.swimming.backend.note.controller;

import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.note.dto.in.TaskOrganizeRequest;
import com.swimming.backend.note.dto.in.TaskOrganizeResponse;
import com.swimming.backend.note.dto.in.TaskOrganizeConfirmRequest;
import com.swimming.backend.note.dto.in.TaskOrganizeConfirmResponse;
import com.swimming.backend.note.usecase.TaskOrganizerUseCase;
import com.swimming.backend.task.domain.TaskStatus;
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

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
    @DisplayName("Preview는 명시된 날짜만 반환하고 confidence를 노출하지 않는다")
    void returnsPreviewWithoutConfidence() throws Exception {
        TaskOrganizeRequest request = new TaskOrganizeRequest(
                7L, "메모", null, null, LocalDate.of(2026, 9, 9)
        );
        when(taskOrganizerUseCase.preview(1L, request)).thenReturn(
                new TaskOrganizeResponse(
                        1L,
                        List.of(
                                new TaskOrganizeResponse.TaskSuggestionResponse(
                                        "item-1",
                                        "내일 처리할 원문",
                                        10L,
                                        "Swimming",
                                        "날짜가 있는 Task",
                                        LocalDate.of(2026, 9, 10)
                                ),
                                new TaskOrganizeResponse.TaskSuggestionResponse(
                                        "item-2",
                                        "날짜 없는 원문",
                                        10L,
                                        "Swimming",
                                        "날짜가 없는 Task",
                                        null
                                )
                        ),
                        List.of()
                )
        );

        mockMvc.perform(post("/api/task-organizer/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"noteId":7,"memo":"메모","currentDate":"2026-09-09"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(1L))
                .andExpect(jsonPath("$.suggestions[0].itemId").value("item-1"))
                .andExpect(jsonPath("$.suggestions[0].folderId").value(10L))
                .andExpect(jsonPath("$.suggestions[0].title").value("날짜가 있는 Task"))
                .andExpect(jsonPath("$.suggestions[0].planDate").value("2026-09-10"))
                .andExpect(jsonPath("$.suggestions[1].planDate").isEmpty())
                .andExpect(jsonPath("$.suggestions[0].confidence").doesNotExist());
    }

    @Test
    @DisplayName("Task Organizer Preview 요청에는 사용자 로컬 기준일이 필요하다")
    void requiresCurrentDateForPreview() throws Exception {
        mockMvc.perform(post("/api/task-organizer/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memo":"내일 로그인 오류 수정"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("승인한 Task를 생성하고 sourceText는 응답에 노출하지 않는다")
    void confirmsTasksWithoutExposingSourceText() throws Exception {
        TaskOrganizeConfirmRequest request = new TaskOrganizeConfirmRequest(
                1L,
                7L,
                List.of(new TaskOrganizeConfirmRequest.ApprovedTaskRequest(
                        "item-1",
                        "원문",
                        10L,
                        "정리된 Task"
                ))
        );
        when(taskOrganizerUseCase.confirm(1L, request)).thenReturn(
                new TaskOrganizeConfirmResponse(
                        List.of(new TaskOrganizeConfirmResponse.CreatedTaskResponse(
                                41L,
                                10L,
                                "정리된 Task",
                                TaskStatus.TODO,
                                false,
                                false
                        ))
                )
        );

        mockMvc.perform(post("/api/task-organizer/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "runId":1,
                                  "noteId":7,
                                  "tasks":[{
                                    "itemId":"item-1",
                                    "sourceText":"원문",
                                    "folderId":10,
                                    "title":"정리된 Task"
                                  }]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdTasks[0].id").value(41L))
                .andExpect(jsonPath("$.createdTasks[0].sourceText").doesNotExist())
                .andExpect(jsonPath("$.remainingNote").doesNotExist());
    }

    @Test
    @DisplayName("미분류 Task의 null folderId를 확정 요청과 응답에서 허용한다")
    void confirmsUnclassifiedTask() throws Exception {
        TaskOrganizeConfirmRequest request = new TaskOrganizeConfirmRequest(
                1L,
                7L,
                List.of(new TaskOrganizeConfirmRequest.ApprovedTaskRequest(
                        "item-1",
                        "운동화 주문",
                        null,
                        "운동화 주문"
                ))
        );
        when(taskOrganizerUseCase.confirm(1L, request)).thenReturn(
                new TaskOrganizeConfirmResponse(
                        List.of(new TaskOrganizeConfirmResponse.CreatedTaskResponse(
                                42L,
                                null,
                                "운동화 주문",
                                TaskStatus.TODO,
                                false,
                                false
                        ))
                )
        );

        mockMvc.perform(post("/api/task-organizer/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "runId":1,
                                  "noteId":7,
                                  "tasks":[{
                                    "itemId":"item-1",
                                    "sourceText":"운동화 주문",
                                    "folderId":null,
                                    "title":"운동화 주문"
                                  }]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdTasks[0].id").value(42L))
                .andExpect(jsonPath("$.createdTasks[0].folderId").isEmpty());

        verify(taskOrganizerUseCase).confirm(1L, request);
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
