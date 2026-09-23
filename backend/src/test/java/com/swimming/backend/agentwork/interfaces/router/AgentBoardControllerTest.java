package com.swimming.backend.agentwork.interfaces.router;
import com.swimming.backend.agentwork.domain.AgentWorkErrorCode;

import com.swimming.backend.agentwork.domain.AgentType;
import com.swimming.backend.agentwork.domain.AgentWorkStatus;
import com.swimming.backend.agentwork.domain.BoardLane;
import com.swimming.backend.agentwork.domain.BoardSort;
import com.swimming.backend.agentwork.domain.StatusSource;
import com.swimming.backend.agentwork.domain.WorkResourceType;
import com.swimming.backend.agentwork.interfaces.router.dto.AddWorkItemRequest;
import com.swimming.backend.agentwork.application.dto.AddWorkItemResult;
import com.swimming.backend.agentwork.application.dto.AgentBoardResponse;
import com.swimming.backend.agentwork.application.dto.AgentSessionResponse;
import com.swimming.backend.agentwork.application.dto.AgentWorkItemResponse;
import com.swimming.backend.agentwork.application.dto.WorkItemResponse;
import com.swimming.backend.agentwork.application.usecase.AgentBoardUseCase;
import com.swimming.backend.agentwork.application.usecase.AgentWorkItemUseCase;
import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.common.exception.GlobalExceptionHandler;
import com.swimming.backend.common.security.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentBoardControllerTest {

    private static final Instant NOW = Instant.parse("2026-09-17T05:00:00Z");

    private AgentBoardUseCase agentBoardUseCase;
    private AgentWorkItemUseCase agentWorkItemUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        agentBoardUseCase = mock(AgentBoardUseCase.class);
        agentWorkItemUseCase = mock(AgentWorkItemUseCase.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AgentBoardController(agentBoardUseCase, agentWorkItemUseCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthUserArgumentResolver(new AuthUser(1L, "user@example.com")))
                .build();
    }

    @Test
    @DisplayName("보드를 Lane 다섯 개로 나눠 반환한다")
    void returnsBoardByLane() throws Exception {
        AgentWorkItemResponse waitingCard = card(10L, BoardLane.WAITING,
                session(100L, AgentWorkStatus.WAITING, "Migration까지 적용할까요?"));
        when(agentBoardUseCase.getBoard(1L, BoardSort.PRIORITY)).thenReturn(new AgentBoardResponse(
                List.of(card(11L, BoardLane.NOT_STARTED, null)),
                List.of(),
                List.of(waitingCard),
                List.of(),
                List.of()
        ));

        mockMvc.perform(get("/api/agent-work/board"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notStarted[0].id").value(11))
                .andExpect(jsonPath("$.notStarted[0].session").doesNotExist())
                .andExpect(jsonPath("$.notStarted[0].sessions").doesNotExist())
                .andExpect(jsonPath("$.working").isEmpty())
                .andExpect(jsonPath("$.waiting[0].lane").value("WAITING"))
                .andExpect(jsonPath("$.waiting[0].workItem.type").value("SWIMMING_TASK"))
                .andExpect(jsonPath("$.waiting[0].workItem.id").value("7"))
                .andExpect(jsonPath("$.waiting[0].workItem.containerName").value("Spring AI 공부"))
                .andExpect(jsonPath("$.waiting[0].session.status").value("WAITING"))
                .andExpect(jsonPath("$.waiting[0].session").isMap())
                .andExpect(jsonPath("$.waiting[0].sessions").doesNotExist())
                .andExpect(jsonPath("$.waiting[0].session.agentType").value("CLAUDE_CODE"))
                .andExpect(jsonPath("$.waiting[0].session.summary").value("Migration까지 적용할까요?"))
                .andExpect(jsonPath("$.completed").isEmpty())
                .andExpect(jsonPath("$.attention").isEmpty());
    }

    @Test
    @DisplayName("기존 할 일을 보드에 새로 올리면 201과 Location을 반환한다")
    void addsWorkItem() throws Exception {
        AddWorkItemRequest request = new AddWorkItemRequest(WorkResourceType.SWIMMING_TASK, "7");
        when(agentWorkItemUseCase.addWorkItem(1L, request))
                .thenReturn(new AddWorkItemResult(card(10L, BoardLane.NOT_STARTED, null), true));

        mockMvc.perform(post("/api/agent-work/work-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceType":"SWIMMING_TASK","resourceId":"7"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/agent-work/work-items/10"))
                .andExpect(jsonPath("$.lane").value("NOT_STARTED"));
    }

    @Test
    @DisplayName("이미 보드에 있는 할 일을 올리면 200으로 기존 카드를 반환한다")
    void returnsExistingWorkItem() throws Exception {
        AddWorkItemRequest request = new AddWorkItemRequest(WorkResourceType.SWIMMING_TASK, "7");
        when(agentWorkItemUseCase.addWorkItem(1L, request))
                .thenReturn(new AddWorkItemResult(card(10L, BoardLane.WORKING,
                        session(100L, AgentWorkStatus.WORKING, "Controller 구현 중")), false));

        mockMvc.perform(post("/api/agent-work/work-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceType":"SWIMMING_TASK","resourceId":"7"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    @DisplayName("resourceId가 비어 있으면 입력 오류로 거부한다")
    void rejectsBlankResourceId() throws Exception {
        mockMvc.perform(post("/api/agent-work/work-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceType":"SWIMMING_TASK","resourceId":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.resourceId").exists());
    }

    @Test
    @DisplayName("보드에 없는 카드를 조회하면 ProblemDetail 404를 반환한다")
    void rejectsMissingWorkItem() throws Exception {
        when(agentWorkItemUseCase.getWorkItem(1L, 99L))
                .thenThrow(new BusinessException(AgentWorkErrorCode.AGENT_WORK_ITEM_NOT_FOUND));

        mockMvc.perform(get("/api/agent-work/work-items/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AGENT_WORK_ITEM_NOT_FOUND"));
    }

    private static AgentWorkItemResponse card(Long id, BoardLane lane, AgentSessionResponse session) {
        return new AgentWorkItemResponse(
                id,
                lane,
                new WorkItemResponse(WorkResourceType.SWIMMING_TASK, "7", "MCP 서버 구현하기",
                        3L, "Spring AI 공부", 0, true, true),
                session,
                NOW
        );
    }

    private static AgentSessionResponse session(Long id, AgentWorkStatus status, String summary) {
        return new AgentSessionResponse(id, java.util.List.of(10L), AgentType.CLAUDE_CODE, status, StatusSource.MCP_REPORT,
                null, summary, NOW, NOW, null);
    }
}
