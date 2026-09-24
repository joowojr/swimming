package com.swimming.backend.agentwork.application.usecase;

import com.swimming.backend.agentwork.domain.AgentWorkErrorCode;

import com.swimming.backend.agentwork.application.dto.AgentBoardResponse;
import com.swimming.backend.agentwork.application.dto.AgentWorkItemResponse;
import com.swimming.backend.agentwork.application.dto.AgentSessionEventResponse;
import com.swimming.backend.agentwork.domain.BoardLane;
import com.swimming.backend.agentwork.domain.BoardSort;
import com.swimming.backend.agentwork.application.dto.AgentSessionResponse;
import com.swimming.backend.agentwork.application.dto.WorkItemResponse;
import com.swimming.backend.agentwork.application.service.AgentWorkItemReadService;
import com.swimming.backend.agentwork.application.service.AgentSessionReadService;
import com.swimming.backend.agentwork.application.service.AgentSessionEventReadService;
import com.swimming.backend.agentwork.application.port.WorkItemId;
import com.swimming.backend.agentwork.application.port.WorkItemPort;
import com.swimming.backend.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Cowork Board 조회와 보드에 할 일 올리기.
 *
 * <p>getBoard는 구현되어 있으며 나머지 메서드는 의사 코드다.
 * Swimming Task는 agentwork 안의 Work Item 어댑터를 통해서만 읽는다.
 */
@Service
@RequiredArgsConstructor
public class AgentBoardUseCase {

    private final AgentWorkItemReadService workItemReadService;
    private final AgentSessionReadService sessionReadService;
    private final AgentSessionEventReadService sessionEventReadService;
    private final WorkItemPort workItemPort;

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public AgentBoardResponse getBoard(Long userId) {
        return getBoard(userId, BoardSort.PRIORITY);
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public AgentBoardResponse getBoard(Long userId, BoardSort sort) {
        // Swimming: 등록·세션 존재 여부와 관계없이 소유한 삭제되지 않은 Task 전체를 배치 조회한다.
        var resources = workItemPort.readAllOwned(userId);
        Map<BoardLane, List<AgentWorkItemResponse>> lanes = new EnumMap<>(BoardLane.class);
        for (BoardLane lane : BoardLane.values()) {
            lanes.put(lane, new ArrayList<>());
        }
        if (resources.isEmpty()) {
            return boardResponse(lanes);
        }
        // DB: 세션 연결 메타데이터만 조회한다. 보드 GET에서 등록 행을 생성하지 않는다.
        var items = workItemReadService.findBoardItems(userId);
        var itemsByResource = items.stream().collect(Collectors.toMap(
                item -> WorkItemId.builder().type(item.resourceType()).id(item.resourceId()).build(), item -> item));
        var sessionIds = resources.stream().map(resource -> itemsByResource.get(
                WorkItemId.builder().type(resource.type()).id(resource.id()).build())).filter(Objects::nonNull)
                .map(item -> item.sessionId()).filter(Objects::nonNull).distinct().toList();
        // 전체 등록 목록을 재사용하여 숨겨진 Task를 포함한 세션의 전체 연결 목록을 조립한다.
        var linkedIds = items.stream().filter(item -> item.sessionId() != null)
                .collect(Collectors.groupingBy(item -> item.sessionId(),
                        Collectors.mapping(item -> item.id(), Collectors.toList())));
        // DB: 화면에 필요한 공유 세션들을 사용자 조건으로 한 번에 조회한다 (잠금 없음).
        var sessions = sessionReadService.findAllOwned(userId, sessionIds).stream()
                .collect(Collectors.toMap(session -> session.getId(), session -> session));
        Map<Long, AgentSessionResponse> sessionResponses = new HashMap<>();
        sessions.forEach((id, session) -> sessionResponses.put(id, new AgentSessionResponse(
                id, linkedIds.get(id), session.getAgentType(), session.getStatus(), session.getStatusSource(),
                session.getInstruction(), session.summary(), session.getStartedAt(),
                session.getLastSeenAt(), session.getCompletedAt())));
        for (var resource : resources) {
            var item = itemsByResource.get(WorkItemId.builder().type(resource.type()).id(resource.id()).build());
            Long sessionId = item == null ? null : item.sessionId();
            var session = sessionId == null ? null : sessions.get(sessionId);
            if (sessionId != null && session == null) {
                throw new BusinessException(AgentWorkErrorCode.AGENT_SESSION_NOT_FOUND);
            }
            // 시작 전 Lane은 Task 상태가 0인 항목만 노출한다. 1=완료, 2=백로그는 제외한다.
            if (session == null && resource.status() != 0) {
                continue;
            }
            BoardLane lane = session == null ? BoardLane.NOT_STARTED : session.boardLane();
            var card = new AgentWorkItemResponse(item == null ? null : item.id(), lane, new WorkItemResponse(
                    resource.type(), resource.id(), resource.title(), resource.containerId(),
                    resource.containerName(), resource.status(), resource.important(), resource.urgent()),
                    sessionResponses.get(sessionId), session == null ? resource.createdAt() : session.getLastSeenAt());
            lanes.get(lane).add(card);
        }
        // 메모리에서 Lane별 정렬: 중요+즉시 → 즉시 → 중요 → 둘 다 아님 → 최근 활동 → 등록 id 내림차순(NULL 마지막) → 리소스 식별자.
        Comparator<AgentWorkItemResponse> ordering = (sort == BoardSort.PRIORITY
                ? Comparator.comparingInt((AgentWorkItemResponse card) -> (card.workItem().urgent() ? 0 : 1)
                        + (card.workItem().important() ? 0 : 2))
                : Comparator.comparingInt((AgentWorkItemResponse card) -> 0))
                .thenComparing(AgentWorkItemResponse::lastActivityAt,
                        sort == BoardSort.ASC ? Comparator.naturalOrder() : Comparator.reverseOrder())
                .thenComparing(AgentWorkItemResponse::id, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(card -> card.workItem().type().name())
                .thenComparing(card -> card.workItem().id(), Comparator.reverseOrder());
        lanes.values().forEach(cards -> cards.sort(ordering));
        return boardResponse(lanes);
    }

    private AgentBoardResponse boardResponse(Map<BoardLane, List<AgentWorkItemResponse>> lanes) {
        return new AgentBoardResponse(lanes.get(BoardLane.NOT_STARTED), lanes.get(BoardLane.WORKING),
                lanes.get(BoardLane.WAITING), lanes.get(BoardLane.COMPLETED), lanes.get(BoardLane.ATTENTION));
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<AgentSessionEventResponse> getSessionEvents(Long userId, Long sessionId) {
        // DB: userId와 sessionId를 함께 조건으로 세션 소유권을 확인한다.
        sessionReadService.findAllOwned(userId, List.of(sessionId)).stream().findFirst()
                .orElseThrow(() -> new BusinessException(AgentWorkErrorCode.AGENT_SESSION_NOT_FOUND));
        // DB: 소유한 세션의 이벤트를 createdAt ASC, id ASC 순으로 조회한다.
        return sessionEventReadService.findAllOwnedBySession(userId, sessionId);
    }
}
