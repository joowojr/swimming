package com.swimming.backend.agentwork.application.usecase;

import com.swimming.backend.agentwork.domain.AgentWorkErrorCode;

import com.swimming.backend.agentwork.domain.BoardLane;
import com.swimming.backend.agentwork.interfaces.router.dto.AddWorkItemRequest;
import com.swimming.backend.agentwork.application.dto.AddWorkItemResult;
import com.swimming.backend.agentwork.application.dto.AgentSessionEventResponse;
import com.swimming.backend.agentwork.application.dto.AgentSessionResponse;
import com.swimming.backend.agentwork.application.dto.AgentWorkItemResponse;
import com.swimming.backend.agentwork.application.dto.TaskContextResponse;
import com.swimming.backend.agentwork.application.dto.WorkItemResponse;
import com.swimming.backend.agentwork.application.dto.AgentWorkItemRow;
import com.swimming.backend.agentwork.application.service.AgentSessionEventReadService;
import com.swimming.backend.agentwork.application.service.AgentSessionReadService;
import com.swimming.backend.agentwork.application.service.AgentWorkItemReadService;
import com.swimming.backend.agentwork.application.service.AgentWorkItemWriteService;
import com.swimming.backend.agentwork.application.port.WorkItem;
import com.swimming.backend.agentwork.application.port.WorkItemId;
import com.swimming.backend.agentwork.application.port.WorkItemPort;
import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AgentWorkItemUseCase {
    private final AgentWorkItemReadService workItemReadService;
    private final AgentWorkItemWriteService workItemWriteService;
    private final AgentSessionReadService sessionReadService;
    private final AgentSessionEventReadService sessionEventReadService;
    private final WorkItemPort workItemPort;

    @Transactional(propagation = Propagation.REQUIRED)
    public AddWorkItemResult addWorkItem(Long userId, AddWorkItemRequest request) {
        WorkItemId resourceId = WorkItemId.builder().type(request.resourceType()).id(request.resourceId()).build();
        WorkItem resource = workItemPort.readAll(userId, List.of(resourceId)).get(resourceId);
        if (resource == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }

        var existing = workItemReadService.findBoardItems(userId).stream()
                .filter(item -> item.resourceType() == request.resourceType()
                        && item.resourceId().equals(request.resourceId()))
                .findFirst();
        Long workItemId = workItemWriteService.registerAndLock(userId, request.resourceType(), request.resourceId(), Instant.now());
        var row = workItemReadService.findBoardItems(userId).stream()
                .filter(item -> Objects.equals(item.id(), workItemId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(AgentWorkErrorCode.AGENT_WORK_ITEM_NOT_FOUND));
        return new AddWorkItemResult(toResponse(userId, row, resource), existing.isEmpty());
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public AgentWorkItemResponse getWorkItem(Long userId, Long workItemId) {
        var row = workItemReadService.findBoardItems(userId).stream()
                .filter(item -> Objects.equals(item.id(), workItemId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(AgentWorkErrorCode.AGENT_WORK_ITEM_NOT_FOUND));
        WorkItemId resourceId = WorkItemId.builder().type(row.resourceType()).id(row.resourceId()).build();
        WorkItem resource = workItemPort.readAll(userId, List.of(resourceId)).get(resourceId);
        if (resource == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        return toResponse(userId, row, resource);
    }

    /** 보드에 등록되지 않은 Task도 조회한다. 이때 카드 식별자·세션은 null이고 Lane은 시작 전이다. */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public TaskContextResponse getTaskContext(Long userId, WorkItemId id) {
        // Swimming: 소유·삭제 여부를 확인하고 식별자를 정규화한다.
        WorkItem resource = workItemPort.readAll(userId, List.of(id)).get(id);
        if (resource == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        var row = workItemReadService.findBoardItems(userId).stream()
                .filter(item -> item.resourceType() == resource.type() && item.resourceId().equals(resource.id()))
                .findFirst()
                .orElseGet(() -> new AgentWorkItemRow(null, resource.type(), resource.id(), null, resource.createdAt()));
        AgentWorkItemResponse card = toResponse(userId, row, resource);
        WorkItemId resourceId = WorkItemId.builder().type(resource.type()).id(resource.id()).build();
        return new TaskContextResponse(card.id(), card.lane(), card.workItem(), card.session(),
                workItemPort.readLinkedSources(userId, resourceId));
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<AgentSessionEventResponse> getEvents(Long userId, Long workItemId) {
        var sessionId = workItemReadService.findSessionId(userId, workItemId)
                .orElse(null);
        return sessionId == null ? List.of() : sessionEventReadService.findAllOwnedBySession(userId, sessionId);
    }

    private AgentWorkItemResponse toResponse(Long userId, AgentWorkItemRow row,
                                             WorkItem resource) {
        AgentSessionResponse session = null;
        BoardLane lane = BoardLane.NOT_STARTED;
        Instant lastActivityAt = row.createdAt();
        if (row.sessionId() != null) {
            var domain = sessionReadService.findAllOwned(userId, List.of(row.sessionId())).stream().findFirst()
                    .orElseThrow(() -> new BusinessException(AgentWorkErrorCode.AGENT_SESSION_NOT_FOUND));
            var linkedIds = workItemReadService.findIdsBySession(userId, row.sessionId());
            session = new AgentSessionResponse(domain.getId(), linkedIds, domain.getAgentType(), domain.getStatus(),
                    domain.getStatusSource(), domain.getInstruction(), domain.summary(), domain.getStartedAt(),
                    domain.getLastSeenAt(), domain.getCompletedAt());
            lane = domain.boardLane();
            lastActivityAt = domain.getLastSeenAt();
        }
        return new AgentWorkItemResponse(row.id(), lane, new WorkItemResponse(resource.type(), resource.id(), resource.title(),
                resource.containerId(), resource.containerName(), resource.status(), resource.important(), resource.urgent()),
                session, lastActivityAt);
    }
}
