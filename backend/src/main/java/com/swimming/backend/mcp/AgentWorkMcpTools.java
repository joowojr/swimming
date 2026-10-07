package com.swimming.backend.mcp;

import com.swimming.backend.agentwork.domain.AgentType;
import com.swimming.backend.agentwork.domain.WorkResourceType;
import com.swimming.backend.agentwork.interfaces.router.dto.AddWorkItemRequest;
import com.swimming.backend.agentwork.interfaces.router.dto.AgentReportRequest;
import com.swimming.backend.agentwork.interfaces.router.dto.StartAgentWorkRequest;
import com.swimming.backend.agentwork.application.dto.AddWorkItemResult;
import com.swimming.backend.agentwork.application.dto.AgentSessionEventResponse;
import com.swimming.backend.agentwork.application.dto.AgentSessionResponse;
import com.swimming.backend.agentwork.application.dto.TaskContextResponse;
import com.swimming.backend.agentwork.domain.AgentWorkErrorCode;
import com.swimming.backend.agentwork.application.usecase.AgentBoardUseCase;
import com.swimming.backend.agentwork.application.usecase.AgentSessionReportUseCase;
import com.swimming.backend.agentwork.application.usecase.AgentWorkItemUseCase;
import com.swimming.backend.agentwork.application.port.WorkItemId;
import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.security.AuthUser;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Agent Work UseCase를 MCP Tool로 노출한다.
 *
 * <p>에이전트가 다루는 식별자는 Task 식별자(resourceType + resourceId)와 start_work가 돌려준 sessionId 두 가지다.
 * 입력은 REST와 같은 DTO로 모아 같은 제약으로 검증한다.
 */
@Component
@RequiredArgsConstructor
public class AgentWorkMcpTools {
    private static final String RESOURCE_TYPE = "Task 출처. 현재는 SWIMMING_TASK만 있다.";
    private static final String RESOURCE_ID = "Task 식별자. 사용자가 전달한 할 일 번호를 문자열로 넣는다. 예: \"42\"";
    private static final String SESSION_ID = "start_work 응답의 session.id";

    private final AgentBoardUseCase boardUseCase;
    private final AgentWorkItemUseCase workItemUseCase;
    private final AgentSessionReportUseCase sessionReportUseCase;
    private final Validator validator;

    /** start_work가 받는 Task 식별자. */
    public record TaskRef(
            @ToolParam(description = RESOURCE_TYPE) WorkResourceType resourceType,
            @ToolParam(description = RESOURCE_ID) String resourceId
    ) {
    }

    @Tool(name = "get_task", description = """
            작업을 시작하기 전에 Task의 맥락을 조회한다. 제목·폴더·상태, 보드 Lane, 현재 Agent 세션,
            연결된 지식 문서(제목·url·요약)를 한 번에 돌려준다.
            보드에 아직 등록되지 않은 Task면 workItemId와 session이 null이다.""")
    public TaskContextResponse getTask(
            @ToolParam(description = RESOURCE_TYPE) WorkResourceType resourceType,
            @ToolParam(description = RESOURCE_ID) String resourceId
    ) {
        requireScope("agent-work:read");
        validate(new AddWorkItemRequest(resourceType, resourceId));
        return workItemUseCase.getTaskContext(currentUserId(),
                WorkItemId.builder().type(resourceType).id(resourceId).build());
    }

    @Tool(name = "get_session_events", description = """
            Agent 세션의 활동 기록을 시간순으로 조회한다. 끝난 세션을 다시 시작했을 때 이전 작업 내역을 확인하는 데 쓴다.""")
    public List<AgentSessionEventResponse> getSessionEvents(
            @ToolParam(description = SESSION_ID + " 또는 get_task 응답의 session.id") Long sessionId
    ) {
        requireScope("agent-work:read");
        return boardUseCase.getSessionEvents(currentUserId(), sessionId);
    }

    @Tool(name = "attach_work_item", description = """
            Task를 Cowork Board에 '시작 전' 카드로 올린다. 이미 올라가 있으면 기존 카드를 돌려준다(created=false).
            작업을 바로 시작할 때는 start_work가 자동으로 등록하므로 이 Tool을 먼저 부를 필요가 없다.""")
    public AddWorkItemResult attachWorkItem(
            @ToolParam(description = RESOURCE_TYPE) WorkResourceType resourceType,
            @ToolParam(description = RESOURCE_ID) String resourceId
    ) {
        requireScope("agent-work:write");
        return workItemUseCase.addWorkItem(currentUserId(), validate(new AddWorkItemRequest(resourceType, resourceId)));
    }

    @Tool(name = "start_work", description = """
            Task 목록을 하나의 Agent 세션으로 시작하고 보드 카드를 '작업 중'으로 바꾼다. 응답의 session.id를 보관해
            complete_work에 넘긴다.
            - 여러 Task를 함께 넘기면 모두 같은 세션을 공유한다. session.workItemIds는 세션에 연결된 전체 카드다.
            - 완료·실패로 끝난 세션의 Task를 다시 시작하면 새 세션을 만들지 않고 같은 세션을 다시 연다.
              이때 이 요청에 없던 연결 카드도 함께 '작업 중'이 된다.
            - 이미 진행 중인 세션이 있거나, 서로 다른 세션에 연결된 Task를 섞으면 거절된다.""")
    public AgentSessionResponse startWork(
            @ToolParam(description = "시작할 Task 목록. 1~100개") List<TaskRef> workItems,
            @ToolParam(description = "호출하는 에이전트 종류") AgentType agentType,
            @ToolParam(description = "사용자가 맡긴 작업 지시를 한두 문장으로 요약. 최대 2000자", required = false)
            String instruction
    ) {
        requireScope("agent-work:write");
        List<AddWorkItemRequest> items = workItems == null ? null : workItems.stream()
                .map(ref -> new AddWorkItemRequest(ref.resourceType(), ref.resourceId())).toList();
        return sessionReportUseCase.start(currentUserId(),
                validate(new StartAgentWorkRequest(items, agentType, instruction))).session();
    }

    @Tool(name = "complete_work", description = """
            Agent 세션을 완료로 보고한다. 세션에 연결된 모든 카드가 '완료'로 바뀌며 Swimming Task 자체의 완료 상태는
            바꾸지 않는다. 이미 끝난 세션이면 거절된다.""")
    public void completeWork(
            @ToolParam(description = SESSION_ID) Long sessionId,
            @ToolParam(description = "보드 카드에 보일 결과 한 줄. 최대 200자") String summary,
            @ToolParam(description = "보관용 상세 결과. 변경 파일, 테스트 결과 등 자유 형식 객체", required = false)
            Map<String, Object> details
    ) {
        requireScope("agent-work:write");
        sessionReportUseCase.complete(currentUserId(), sessionId, validate(new AgentReportRequest(summary, details)));
    }

    private <T> T validate(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        return request;
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser authUser) {
            return authUser.id();
        }
        throw new IllegalStateException("MCP 호출 사용자 인증을 확인할 수 없습니다.");
    }

    private void requireScope(String scope) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.getAuthorities().contains(new SimpleGrantedAuthority("SCOPE_" + scope))) {
            throw new BusinessException(AgentWorkErrorCode.AGENT_ACCESS_TOKEN_SCOPE_DENIED);
        }
    }
}
