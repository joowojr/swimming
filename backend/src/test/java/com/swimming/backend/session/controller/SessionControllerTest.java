package com.swimming.backend.session.controller;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.common.exception.GlobalExceptionHandler;
import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.session.domain.SessionStatus;
import com.swimming.backend.session.domain.SessionType;
import com.swimming.backend.session.dto.web.SessionTaskResponse;
import com.swimming.backend.session.dto.web.SessionResponse;
import com.swimming.backend.session.dto.web.SessionDetailResponse;
import com.swimming.backend.session.dto.web.SessionDetailPlaceResponse;
import com.swimming.backend.session.dto.web.SessionPlaceResponse;
import com.swimming.backend.session.dto.web.StartPersonalSessionRequest;
import com.swimming.backend.session.dto.web.UpdateSessionMusicUrlRequest;
import com.swimming.backend.session.dto.web.UpdateSessionPlannedDurationRequest;
import com.swimming.backend.place.domain.BackgroundAssetType;
import com.swimming.backend.place.dto.BackgroundAssetResponse;
import com.swimming.backend.session.usecase.SessionUseCase;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SessionControllerTest {

    private static final Instant STARTED_AT = Instant.parse("2026-08-20T00:00:00Z");

    private SessionUseCase sessionUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        sessionUseCase = mock(SessionUseCase.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SessionController(sessionUseCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthUserArgumentResolver(
                        new AuthUser(1L, "user@example.com")
                ))
                .build();
    }

    @Test
    @DisplayName("개인 세션을 생성하면 Location과 진행 상태를 반환한다")
    void startsPersonalSession() throws Exception {
        StartPersonalSessionRequest request = new StartPersonalSessionRequest(
                List.of(10L, 11L), 20L, 1500
        );
        when(sessionUseCase.startPersonal(1L, request)).thenReturn(new SessionResponse(
                5L,
                SessionType.PERSONAL,
                List.of(10L, 11L),
                sessionPlace(),
                null,
                1500,
                null,
                STARTED_AT,
                null,
                SessionStatus.IN_PROGRESS
        ));

        mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskIds":[10,11],
                                  "placeId":20,
                                  "plannedDurationSec":1500
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/sessions/5"))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.type").value("PERSONAL"))
                .andExpect(jsonPath("$.taskIds[0]").value(10))
                .andExpect(jsonPath("$.taskIds[1]").value(11))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        verify(sessionUseCase).startPersonal(1L, request);
    }

    @Test
    @DisplayName("세션 피드는 현재 사용자의 세션 목록을 반환한다")
    void getsSessionFeed() throws Exception {
        when(sessionUseCase.getAll(1L)).thenReturn(List.of(new SessionDetailResponse(
                5L,
                SessionType.PERSONAL,
                SessionStatus.COMPLETED,
                1500,
                1200,
                STARTED_AT,
                STARTED_AT.plusSeconds(1200),
                sessionDetailPlace(),
                null,
                List.of(new SessionTaskResponse(10L, 2L, "폴더", "첫 Task", true))
        )));

        mockMvc.perform(get("/api/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].tasks[0].title").value("첫 Task"))
                .andExpect(jsonPath("$[0].tasks[0].isCompleted").value(true));

        verify(sessionUseCase).getAll(1L);
    }

    @Test
    @DisplayName("허용 범위를 벗어난 집중 시간은 필드 오류를 반환한다")
    void rejectsInvalidDuration() throws Exception {
        mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskId":10,
                                  "plannedDurationSec":59
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.plannedDurationSec").exists());
    }

    @Test
    @DisplayName("진행 중인 세션과 선택한 Task 목록을 순서대로 반환한다")
    void getsActiveSession() throws Exception {
        when(sessionUseCase.getActive(1L)).thenReturn(java.util.Optional.of(
                new SessionDetailResponse(
                        5L,
                        SessionType.PERSONAL,
                        SessionStatus.IN_PROGRESS,
                        1500,
                        null,
                        STARTED_AT,
                        null,
                        sessionDetailPlace(),
                        "https://youtu.be/example",
                        List.of(
                                new SessionTaskResponse(10L, 2L, "폴더", "첫 Task", null),
                                new SessionTaskResponse(11L, 2L, "폴더", "다음 Task", null)
                        )
                )
        ));

        mockMvc.perform(get("/api/sessions/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.tasks[0].id").value(10))
                .andExpect(jsonPath("$.tasks[0].projectName").value("폴더"))
                .andExpect(jsonPath("$.tasks[1].id").value(11));
    }

    @Test
    @DisplayName("진행 중인 세션이 없으면 본문 없이 응답한다")
    void returnsNoContentWithoutActiveSession() throws Exception {
        when(sessionUseCase.getActive(1L)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/sessions/active"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("세션을 종료하면 서버가 확정한 실제 집중 시간을 반환한다")
    void endsPersonalSession() throws Exception {
        when(sessionUseCase.end(eq(1L), eq(5L), any())).thenReturn(new SessionResponse(
                5L,
                SessionType.PERSONAL,
                List.of(10L, 11L),
                sessionPlace(),
                null,
                1500,
                600,
                STARTED_AT,
                STARTED_AT.plusSeconds(600),
                SessionStatus.INTERRUPTED
        ));

        mockMvc.perform(post("/api/sessions/5/end"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualDurationSec").value(600))
                .andExpect(jsonPath("$.endedAt").value("2026-08-20T00:10:00Z"))
                .andExpect(jsonPath("$.status").value("INTERRUPTED"));
    }

    @Test
    @DisplayName("진행 중인 세션이 있으면 ProblemDetail 충돌 응답을 반환한다")
    void returnsConflictForExistingActiveSession() throws Exception {
        StartPersonalSessionRequest request = new StartPersonalSessionRequest(
                List.of(10L, 11L), 20L, 1500
        );
        doThrow(new BusinessException(ErrorCode.ACTIVE_SESSION_ALREADY_EXISTS))
                .when(sessionUseCase).startPersonal(1L, request);

        mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskIds":[10,11],
                                  "placeId":20,
                                  "plannedDurationSec":1500
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ACTIVE_SESSION_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("세션 단건 조회는 공간과 마지막 음악 URL을 반환한다")
    void getsSessionDetail() throws Exception {
        when(sessionUseCase.get(1L, 5L)).thenReturn(new SessionDetailResponse(
                5L,
                SessionType.PERSONAL,
                SessionStatus.IN_PROGRESS,
                1500,
                null,
                STARTED_AT,
                null,
                sessionDetailPlace(),
                "https://youtu.be/example",
                List.of(new SessionTaskResponse(10L, 2L, "폴더", "첫 Task", null))
        ));

        mockMvc.perform(get("/api/sessions/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.place.id").value(20))
                .andExpect(jsonPath("$.place.backgroundAsset.type").value("VIDEO"))
                .andExpect(jsonPath("$.musicUrl").value("https://youtu.be/example"));
    }

    @Test
    @DisplayName("세션의 마지막 음악 URL을 저장하면 본문 없이 응답한다")
    void updatesMusicUrl() throws Exception {
        UpdateSessionMusicUrlRequest request = new UpdateSessionMusicUrlRequest(
                "https://www.youtube.com/watch?v=example"
        );

        mockMvc.perform(put("/api/sessions/5/music-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "musicUrl":"https://www.youtube.com/watch?v=example"
                                }
                                """))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(sessionUseCase).updateMusicUrl(1L, 5L, request);
    }

    @Test
    @DisplayName("음악 URL이 최대 길이를 넘으면 필드 오류를 반환한다")
    void rejectsTooLongMusicUrl() throws Exception {
        String longUrl = "https://www.youtube.com/watch?v=" + "a".repeat(2049);

        mockMvc.perform(put("/api/sessions/5/music-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"musicUrl\":\"" + longUrl + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.musicUrl").exists());
    }

    @Test
    @DisplayName("진행 중인 세션의 집중 시간을 변경하면 본문 없이 응답한다")
    void updatesPlannedDuration() throws Exception {
        UpdateSessionPlannedDurationRequest request =
                new UpdateSessionPlannedDurationRequest(1800);

        mockMvc.perform(put("/api/sessions/5/planned-duration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "plannedDurationSec":1800
                                }
                                """))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(sessionUseCase).updatePlannedDuration(1L, 5L, request);
    }

    @Test
    @DisplayName("허용 범위를 벗어난 세션 집중 시간 변경은 필드 오류를 반환한다")
    void rejectsInvalidPlannedDurationUpdate() throws Exception {
        mockMvc.perform(put("/api/sessions/5/planned-duration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "plannedDurationSec":59
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.plannedDurationSec").exists());
    }

    private SessionPlaceResponse sessionPlace() {
        return new SessionPlaceResponse(
                20L,
                3L,
                "Lisbon",
                "Alfama Cafe",
                "https://youtu.be/default"
        );
    }

    private SessionDetailPlaceResponse sessionDetailPlace() {
        return new SessionDetailPlaceResponse(
                20L,
                3L,
                "Lisbon",
                "Alfama Cafe",
                new BackgroundAssetResponse(
                        BackgroundAssetType.VIDEO,
                        "places/video/alfama.mp4",
                        "https://bucket.s3.amazonaws.com/alfama.mp4?X-Amz-Signature=abc"
                ),
                "https://youtu.be/default"
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
