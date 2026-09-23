package com.swimming.backend.agentwork.usecase;

import com.swimming.backend.agentwork.dto.out.AgentSessionResponse;
import org.springframework.stereotype.Service;

/** 세션 조회와 무응답 처리. MCP Tool의 상태 보고는 AgentSessionReportUseCase가 담당한다. */
@Service
public class AgentSessionUseCase {

    public AgentSessionResponse getSession(Long userId, Long sessionId) {
        /**
         * <pre>
         * DB: session = AgentSessionReadService가 (userId, sessionId)로 조회
         * 없거나 타인 소유 → AGENT_SESSION_NOT_FOUND (404)
         * summary = 도메인 객체가 현재 상태에 따라 계산:
         *     COMPLETED → resultSnapshot.summary, FAILED → errorSnapshot.summary
         *     WORKING·WAITING·UNKNOWN → progressSnapshot.summary
         *     해당 snapshot이 없으면 null (최초 실행·재시작 직후)
         * DB: workItemIds = WorkItemService가 userId·sessionId로 전체 연결 Work Item id 목록 조회
         * return AgentSessionResponse(id, workItemIds, agentType, status, statusSource,
         *                             instruction, summary, startedAt, lastSeenAt, completedAt)
         * details·이벤트 payload 원문은 응답에 노출하지 않음
         * </pre>
         */
        throw new UnsupportedOperationException("의사 코드: getSession");
    }

    public void markSilentSessionsUnknown() {
        /**
         * 1분마다 스케줄러가 부른다.
         * <pre>
         * now = 현재 시각, threshold = now - app.agent-work.stale-after(30m)
         * DB: candidates = AgentSessionReadService가 WORKING &amp;&amp; lastSeenAt &lt; threshold인 세션 식별자 조회
         * 각 후보마다 별도 트랜잭션에서:
         *     DB: session = AgentSessionWriteService가 후보 userId·sessionId로 잠금 재조회
         *     session.markUnknownIfSilent(threshold)
         *         현재 WORKING &amp;&amp; lastSeenAt &lt; threshold일 때만 UNKNOWN으로 전이
         *         조회 후 새 보고가 왔거나 상태가 바뀌었으면 건너뜀
         *         WAITING·COMPLETED·FAILED·UNKNOWN은 변경하지 않음
         *     전이가 일어났으면:
         *         DB: AgentSessionWriteService가 단건 변경을 Entity에 반영 (변경 감지)
         *         DB: AgentSessionEventWriteService가 SIGNAL_LOST 기록
         *             sessionId, 기록 당시 agentType, source = session.statusSource, createdAt = now
         *     lastSeenAt은 실제 마지막 보고 시각으로 유지, completedAt·snapshot도 유지
         *     상태 변경과 이벤트 기록은 함께 커밋·롤백 (한 번의 전이에 이벤트 하나, 공유 세션의 모든 카드에 같은 상태 적용)
         * 개별 이벤트가 필요한 상태 전이이므로 여러 세션을 한 번에 벌크 변경하지 않음
         * </pre>
         */
        throw new UnsupportedOperationException("의사 코드: markSilentSessionsUnknown");
    }
}
