package com.swimming.backend.agentwork.application.usecase;

import com.swimming.backend.agentwork.domain.AgentWorkErrorCode;

import com.swimming.backend.agentwork.interfaces.router.dto.AgentReportRequest;
import com.swimming.backend.agentwork.interfaces.router.dto.StartAgentWorkRequest;
import com.swimming.backend.agentwork.application.dto.AgentSessionResponse;
import com.swimming.backend.agentwork.application.dto.StartAgentWorkResult;
import com.swimming.backend.agentwork.domain.AgentSession;
import com.swimming.backend.agentwork.application.event.AgentWorkStatusChangedEvent;
import com.swimming.backend.agentwork.application.service.AgentWorkItemReadService;
import com.swimming.backend.agentwork.application.service.AgentWorkItemWriteService;
import com.swimming.backend.agentwork.application.service.AgentSessionWriteService;
import com.swimming.backend.agentwork.application.service.AgentSessionEventWriteService;
import com.swimming.backend.agentwork.application.port.WorkItemId;
import com.swimming.backend.agentwork.application.port.WorkItemReader;
import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Comparator;
import java.util.LinkedHashSet;

/**
 * MCP Tool로 상태를 보고하는 경로. 상태 REST API도 같은 유스케이스를 호출한다.
 *
 * <p>start·complete는 구현되어 있으며 나머지 보고 메서드는 의사 코드다.
 *
 * <pre>
 * 상태 전이 (끝난 세션 = COMPLETED·FAILED 에 보고하면 AGENT_SESSION_ALREADY_ENDED)
 *   progress : WORKING·WAITING·UNKNOWN → WORKING
 *   wait     : WORKING·WAITING·UNKNOWN → WAITING
 *   complete : WORKING·WAITING·UNKNOWN → COMPLETED (completedAt = now)
 *   fail     : WORKING·WAITING·UNKNOWN → FAILED    (completedAt = now)
 * 모든 보고: lastSeenAt = now, agent_session_events에 {summary, details}와 당시 agentType 기록
 * </pre>
 *
 * <p>외부 입출력은 DB: (조회·잠금·저장)와 Swimming: (Work Item 어댑터 호출)로 표시한다.
 * 도메인 객체의 검증·전이·응답 계산은 외부 입출력이 아니다.
 *
 * <p>의사 코드에서 UseCase는 Service를 통해 순수 도메인 객체를 조회·조율한다.
 * 상태 검증·전이는 도메인 객체가 담당하고, Service는 결과를 Entity에 반영한다.
 * 단건 수정은 변경 감지를 사용한다. 세션 변경과 이벤트 기록은 같은 트랜잭션에서 처리한다
 * (propagation = Propagation.REQUIRED). 미구현 보고 메서드의 주석은 구현 전 설계 표현이다.
 *
 * <p>여러 할 일(work item)이 하나의 세션을 공유한다. 다시 시작하면 새 세션을 만들지 않고 끝난 세션을 다시 연다.
 */
@Service
@RequiredArgsConstructor
public class AgentSessionReportUseCase {
    private final WorkItemReader workItemReader;
    private final AgentWorkItemReadService workItemReadService;
    private final AgentWorkItemWriteService workItemWriteService;
    private final AgentSessionWriteService sessionWriteService;
    private final AgentSessionEventWriteService eventWriteService;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRED)
    public StartAgentWorkResult start(Long userId, StartAgentWorkRequest request) {
        // Swimming: 모든 요청 Task의 소유권·삭제 여부를 한 번에 검증한다.
        List<WorkItemId> requestedIds = request.workItems().stream()
                .map(item -> WorkItemId.builder().type(item.resourceType()).id(item.resourceId()).build()).distinct().toList();
        var resources = workItemReader.readAll(userId, requestedIds);
        if (requestedIds.stream().anyMatch(id -> !resources.containsKey(id))) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        Instant now = clock.instant();
        // DB: 정규 식별자를 중복 제거하고 항상 같은 순서로 등록·잠금하여 교차 요청의 데드락을 방지한다.
        List<Long> workItemIds = requestedIds.stream().map(resources::get)
                .map(item -> WorkItemId.builder().type(item.type()).id(item.id()).build()).distinct()
                .sorted(Comparator.comparing((WorkItemId id) -> id.type().name()).thenComparing(WorkItemId::id))
                .map(id -> workItemWriteService.registerAndLock(userId, id.type(), id.id(), now)).toList();
        var sessionIds = new LinkedHashSet<Long>();
        for (Long workItemId : workItemIds) {
            // DB: 잠긴 Work Item이 참조하는 세션 식별자를 조회한다.
            workItemReadService.findSessionId(userId, workItemId).ifPresent(sessionIds::add);
        }
        if (sessionIds.size() > 1) {
            // 서로 다른 기존 세션을 합치거나 Work Item의 세션을 교체하지 않는다.
            throw new BusinessException(AgentWorkErrorCode.AGENT_SESSION_CONFLICT);
        }
        boolean created = sessionIds.isEmpty();
        AgentSession session;
        if (created) {
            // DB: 여러 Work Item이 공유할 세션을 하나 생성한다.
            session = sessionWriteService.create(AgentSession.start(userId, request.agentType(), request.instruction(), now));
        } else {
            // DB: 서로 다른 카드로 들어온 재시작도 공유 세션 잠금으로 직렬화한다.
            session = sessionWriteService.findOwnedForUpdate(userId, sessionIds.iterator().next())
                    .orElseThrow(() -> new BusinessException(AgentWorkErrorCode.AGENT_SESSION_NOT_FOUND));
            session.restart(request.agentType(), request.instruction(), now);
            // DB: 같은 세션을 다시 열므로 요청에 없는 기존 연결 카드에도 상태가 적용된다.
            sessionWriteService.applyState(session);
        }
        // DB: 잠긴 Work Item 목록을 벌크 JPQL로 연결하고 영향 행 수를 검증한다.
        // updatedAt을 명시하고 실행 전 flush·실행 후 clear로 영속성 컨텍스트를 동기화한다.
        workItemWriteService.attachSession(userId, workItemIds, session.getId(), now);
        // DB: 연결·세션 상태·STARTED 이벤트를 같은 트랜잭션에서 저장한다.
        eventWriteService.recordStarted(session);
        eventPublisher.publishEvent(new AgentWorkStatusChangedEvent(
                userId, session.getId(), session.getStatus(), now));
        // DB: 응답에는 요청 목록뿐 아니라 세션에 연결된 전체 Work Item을 포함한다.
        List<Long> linkedIds = workItemReadService.findIdsBySession(userId, session.getId());
        return new StartAgentWorkResult(new AgentSessionResponse(
                session.getId(), linkedIds, session.getAgentType(), session.getStatus(),
                session.getStatusSource(), session.getInstruction(), session.summary(),
                session.getStartedAt(), session.getLastSeenAt(), session.getCompletedAt()), created);
    }

    public void reportProgress(Long userId, Long sessionId, AgentReportRequest request) {
        /**
         * <pre>
         * 트랜잭션 시작:
         *     DB: session = AgentSessionWriteService가 (userId, sessionId)로 잠금 조회
         *     없거나 타인 소유 → AGENT_SESSION_NOT_FOUND (404)
         *     now = 현재 시각
         *     session.reportProgress({summary, details}, now)
         *         COMPLETED·FAILED → AGENT_SESSION_ALREADY_ENDED (409)
         *         WORKING·WAITING·UNKNOWN → WORKING
         *         progressSnapshot = {summary, details}, lastSeenAt = now, statusSource = MCP_REPORT
         *     DB: AgentSessionWriteService가 도메인 전이 결과를 Entity에 반영 (변경 감지)
         *     DB: AgentSessionEventWriteService가 PROGRESS_REPORTED 기록
         *         sessionId, 기록 당시 agentType, payload = {summary, details},
         *         source = MCP_REPORT, createdAt = now
         *     세션 반영 또는 이벤트 기록 실패 시 함께 롤백
         * </pre>
         */
        throw new UnsupportedOperationException("의사 코드: reportProgress");
    }

    public void waitForUser(Long userId, Long sessionId, AgentReportRequest request) {
        /**
         * <pre>
         * 트랜잭션 시작:
         *     DB: session = AgentSessionWriteService가 (userId, sessionId)로 잠금 조회
         *     없거나 타인 소유 → AGENT_SESSION_NOT_FOUND (404)
         *     now = 현재 시각
         *     session.waitForUser({summary, details}, now)
         *         COMPLETED·FAILED → AGENT_SESSION_ALREADY_ENDED (409)
         *         WORKING·WAITING·UNKNOWN → WAITING
         *         progressSnapshot = {summary, details}, lastSeenAt = now, statusSource = MCP_REPORT
         *     DB: AgentSessionWriteService가 도메인 전이 결과를 Entity에 반영 (변경 감지)
         *     DB: AgentSessionEventWriteService가 WAITING_FOR_USER 기록
         *         sessionId, 기록 당시 agentType, payload = {summary, details},
         *         source = MCP_REPORT, createdAt = now
         *     세션 반영 또는 이벤트 기록 실패 시 함께 롤백
         * </pre>
         */
        throw new UnsupportedOperationException("의사 코드: waitForUser");
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void complete(Long userId, Long sessionId, AgentReportRequest request) {
        // DB: 소유한 세션을 잠가 재시작·다른 완료 보고와의 경합을 직렬화한다.
        AgentSession session = sessionWriteService.findOwnedForUpdate(userId, sessionId)
                .orElseThrow(() -> new BusinessException(AgentWorkErrorCode.AGENT_SESSION_NOT_FOUND));
        session.complete(request.summary(), request.details(), clock.instant());
        // DB: 단건 세션 상태·결과·완료 시각은 변경 감지로 반영한다.
        sessionWriteService.applyState(session);
        // DB: COMPLETED 이벤트도 같은 트랜잭션에서 저장하여 실패하면 상태 변경을 롤백한다.
        eventWriteService.recordCompleted(session);
        eventPublisher.publishEvent(new AgentWorkStatusChangedEvent(
                userId, session.getId(), session.getStatus(), session.getLastSeenAt()));
        // 공유 세션의 모든 연결 카드가 COMPLETED를 따른다. Swimming Task 상태는 변경하지 않는다.
    }

    public void fail(Long userId, Long sessionId, AgentReportRequest request) {
        /**
         * <pre>
         * 트랜잭션 시작:
         *     DB: session = AgentSessionWriteService가 (userId, sessionId)로 잠금 조회
         *     없거나 타인 소유 → AGENT_SESSION_NOT_FOUND (404)
         *     now = 현재 시각
         *     session.fail({summary, details}, now)
         *         COMPLETED·FAILED → AGENT_SESSION_ALREADY_ENDED (409)
         *         WORKING·WAITING·UNKNOWN → FAILED
         *         errorSnapshot = {summary, details}, lastSeenAt = now, statusSource = MCP_REPORT
         *         completedAt = now
         *     DB: AgentSessionWriteService가 도메인 전이 결과를 Entity에 반영 (변경 감지)
         *     DB: AgentSessionEventWriteService가 FAILED 기록
         *         sessionId, 기록 당시 agentType, payload = {summary, details},
         *         source = MCP_REPORT, createdAt = now
         *     세션 반영 또는 이벤트 기록 실패 시 함께 롤백
         * </pre>
         */
        throw new UnsupportedOperationException("의사 코드: fail");
    }
}
