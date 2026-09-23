package com.swimming.backend.agentwork.controller;
import com.swimming.backend.agentwork.exception.AgentWorkErrorCode;

import com.swimming.backend.agentwork.domain.AgentType;
import com.swimming.backend.agentwork.domain.AgentWorkStatus;
import com.swimming.backend.agentwork.domain.StatusSource;
import com.swimming.backend.agentwork.domain.WorkResourceType;
import com.swimming.backend.agentwork.dto.in.AgentReportRequest;
import com.swimming.backend.agentwork.dto.in.StartAgentWorkRequest;
import com.swimming.backend.agentwork.dto.in.AddWorkItemRequest;
import com.swimming.backend.agentwork.dto.out.AgentSessionResponse;
import com.swimming.backend.agentwork.dto.out.StartAgentWorkResult;
import com.swimming.backend.agentwork.usecase.AgentSessionReportUseCase;
import com.swimming.backend.agentwork.usecase.AgentSessionUseCase;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentSessionControllerTest {

    private static final Instant NOW = Instant.parse("2026-09-17T05:00:00Z");

    private AgentSessionReportUseCase agentSessionReportUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        agentSessionReportUseCase = mock(AgentSessionReportUseCase.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AgentSessionController(mock(AgentSessionUseCase.class), agentSessionReportUseCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthUserArgumentResolver(new AuthUser(1L, "user@example.com")))
                .build();
    }

    @Test
    @DisplayName("세션이 없는 할 일로 작업을 시작하면 201과 세션 Location을 반환한다")
    void startsWork() throws Exception {
        StartAgentWorkRequest request = new StartAgentWorkRequest(java.util.List.of(new AddWorkItemRequest(WorkResourceType.SWIMMING_TASK, "7")), AgentType.CLAUDE_CODE, "Controller 구현");
        when(agentSessionReportUseCase.start(1L, request)).thenReturn(new StartAgentWorkResult(workingSession(), true));

        mockMvc.perform(post("/api/agent-work/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workItems":[{"resourceType":"SWIMMING_TASK","resourceId":"7"}],
                                 "agentType":"CLAUDE_CODE","instruction":"Controller 구현"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/agent-work/sessions/100"))
                .andExpect(jsonPath("$.workItemIds[0]").value(10))
                .andExpect(jsonPath("$.status").value("WORKING"))
                .andExpect(jsonPath("$.statusSource").value("MCP_REPORT"));
    }

    @Test
    @DisplayName("끝난 세션을 다시 열면 200으로 같은 세션을 반환한다")
    void reopensEndedSession() throws Exception {
        StartAgentWorkRequest request = new StartAgentWorkRequest(java.util.List.of(new AddWorkItemRequest(WorkResourceType.SWIMMING_TASK, "7")), AgentType.CLAUDE_CODE, "Controller 구현");
        when(agentSessionReportUseCase.start(1L, request)).thenReturn(new StartAgentWorkResult(workingSession(), false));

        mockMvc.perform(post("/api/agent-work/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workItems":[{"resourceType":"SWIMMING_TASK","resourceId":"7"}],
                                 "agentType":"CLAUDE_CODE","instruction":"Controller 구현"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.id").value(100));
    }

    @Test
    @DisplayName("진행 중인 세션이 있는 할 일로 시작하면 ProblemDetail 409를 반환한다")
    void rejectsStartWhileInProgress() throws Exception {
        when(agentSessionReportUseCase.start(eq(1L), any()))
                .thenThrow(new BusinessException(AgentWorkErrorCode.AGENT_WORK_ALREADY_IN_PROGRESS));

        mockMvc.perform(post("/api/agent-work/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workItems":[{"resourceType":"SWIMMING_TASK","resourceId":"7"}],"agentType":"CODEX"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AGENT_WORK_ALREADY_IN_PROGRESS"));
    }

    @Test
    @DisplayName("지원하지 않는 에이전트 종류는 400으로 거부한다")
    void rejectsUnknownAgentType() throws Exception {
        mockMvc.perform(post("/api/agent-work/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workItems":[{"resourceType":"SWIMMING_TASK","resourceId":"7"}],"agentType":"PI"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(agentSessionReportUseCase);
    }

    @Test
    @DisplayName("진행·대기·완료·실패 보고는 204를 반환한다")
    void acceptsReports() throws Exception {
        String body = """
                {"summary":"MCP Tool 구현 중","details":{"step":"controller"}}
                """;
        AgentReportRequest request = new AgentReportRequest("MCP Tool 구현 중", Map.of("step", "controller"));

        for (String action : new String[]{"progress", "wait", "complete", "fail"}) {
            mockMvc.perform(post("/api/agent-work/sessions/100/" + action)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNoContent());
        }

        verify(agentSessionReportUseCase).reportProgress(1L, 100L, request);
        verify(agentSessionReportUseCase).waitForUser(1L, 100L, request);
        verify(agentSessionReportUseCase).complete(1L, 100L, request);
        verify(agentSessionReportUseCase).fail(1L, 100L, request);
    }

    @Test
    @DisplayName("요약이 비었거나 200자를 넘으면 입력 오류로 거부한다")
    void rejectsInvalidSummary() throws Exception {
        mockMvc.perform(post("/api/agent-work/sessions/100/progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.summary").exists());

        mockMvc.perform(post("/api/agent-work/sessions/100/progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"" + "가".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.summary").exists());
    }

    @Test
    @DisplayName("끝난 세션에 보고하면 ProblemDetail 409를 반환한다")
    void rejectsReportOnEndedSession() throws Exception {
        doThrow(new BusinessException(AgentWorkErrorCode.AGENT_SESSION_ALREADY_ENDED))
                .when(agentSessionReportUseCase).reportProgress(eq(1L), eq(100L), any());

        mockMvc.perform(post("/api/agent-work/sessions/100/progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"다시 시작\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AGENT_SESSION_ALREADY_ENDED"));
    }

    @Test
    @DisplayName("시작할 Work Item 목록이 없거나 비었거나 항목이 유효하지 않으면 400을 반환한다")
    void rejectsInvalidWorkItemList() throws Exception {
        for (String body : new String[]{
                "{\"agentType\":\"CODEX\"}",
                "{\"workItems\":[],\"agentType\":\"CODEX\"}",
                "{\"workItems\":[null],\"agentType\":\"CODEX\"}",
                "{\"workItems\":[{\"resourceType\":\"SWIMMING_TASK\",\"resourceId\":\"\"}],\"agentType\":\"CODEX\"}",
                "{\"workItems\":[{\"resourceId\":\"7\"}],\"agentType\":\"CODEX\"}"}) {
            mockMvc.perform(post("/api/agent-work/sessions").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(agentSessionReportUseCase);
    }

    @Test
    @DisplayName("여러 Work Item의 시작 계약은 배열 입력과 연결된 Work Item 배열 응답을 사용한다")
    void startsGroupedWorkItems() throws Exception {
        var request = new StartAgentWorkRequest(java.util.List.of(
                new AddWorkItemRequest(WorkResourceType.SWIMMING_TASK, "7"),
                new AddWorkItemRequest(WorkResourceType.SWIMMING_TASK, "8")), AgentType.CODEX, null);
        var response = new AgentSessionResponse(100L, java.util.List.of(10L, 11L), AgentType.CODEX,
                AgentWorkStatus.WORKING, StatusSource.MCP_REPORT, null, null, NOW, NOW, null);
        when(agentSessionReportUseCase.start(1L, request)).thenReturn(new StartAgentWorkResult(response, true));
        mockMvc.perform(post("/api/agent-work/sessions").contentType(MediaType.APPLICATION_JSON).content("""
                {"workItems":[{"resourceType":"SWIMMING_TASK","resourceId":"7"},
                              {"resourceType":"SWIMMING_TASK","resourceId":"8"}],"agentType":"CODEX"}
                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.workItemIds[1]").value(11))
                .andExpect(jsonPath("$.workItemId").doesNotExist());
        verify(agentSessionReportUseCase).start(1L, request);
    }

    private static AgentSessionResponse workingSession() {
        return new AgentSessionResponse(
                100L, java.util.List.of(10L), AgentType.CLAUDE_CODE, AgentWorkStatus.WORKING, StatusSource.MCP_REPORT,
                "Controller 구현", null, NOW, NOW, null
        );
    }
}
