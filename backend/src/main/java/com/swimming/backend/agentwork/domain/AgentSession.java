package com.swimming.backend.agentwork.domain;

import com.swimming.backend.common.exception.BusinessException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** 여러 Work Item이 공유할 수 있는 세션. 실행을 다시 시작해도 식별자는 유지한다. */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AgentSession {
    private final Long id;
    private final Long userId;
    private AgentType agentType;
    private AgentWorkStatus status;
    private StatusSource statusSource;
    private String instruction;
    private Map<String, Object> progressSnapshot;
    private Map<String, Object> resultSnapshot;
    private Map<String, Object> errorSnapshot;
    private Instant startedAt;
    private Instant lastSeenAt;
    private Instant completedAt;

    public static AgentSession start(Long userId, AgentType agentType,
                                     String instruction, Instant now) {
        return AgentSession.builder()
                .userId(userId).agentType(agentType).status(AgentWorkStatus.WORKING)
                .statusSource(StatusSource.MCP_REPORT).instruction(instruction)
                .startedAt(now).lastSeenAt(now).build();
    }

    public static AgentSession restore(Long id, Long userId, AgentType agentType,
                                       AgentWorkStatus status, StatusSource statusSource, String instruction,
                                       Map<String, Object> progressSnapshot, Map<String, Object> resultSnapshot,
                                       Map<String, Object> errorSnapshot, Instant startedAt, Instant lastSeenAt,
                                       Instant completedAt) {
        return AgentSession.builder()
                .id(id).userId(userId).agentType(agentType).status(status).statusSource(statusSource)
                .instruction(instruction).progressSnapshot(progressSnapshot).resultSnapshot(resultSnapshot)
                .errorSnapshot(errorSnapshot).startedAt(startedAt).lastSeenAt(lastSeenAt)
                .completedAt(completedAt).build();
    }

    public void restart(AgentType agentType, String instruction, Instant now) {
        if (status != AgentWorkStatus.COMPLETED && status != AgentWorkStatus.FAILED) {
            throw new BusinessException(AgentWorkErrorCode.AGENT_WORK_ALREADY_IN_PROGRESS);
        }
        this.agentType = agentType;
        this.instruction = instruction;
        this.status = AgentWorkStatus.WORKING;
        this.statusSource = StatusSource.MCP_REPORT;
        this.startedAt = now;
        this.lastSeenAt = now;
        this.completedAt = null;
        this.progressSnapshot = null;
        this.resultSnapshot = null;
        this.errorSnapshot = null;
    }

    public void complete(String summary, Map<String, Object> details, Instant now) {
        if (status == AgentWorkStatus.COMPLETED || status == AgentWorkStatus.FAILED) {
            throw new BusinessException(AgentWorkErrorCode.AGENT_SESSION_ALREADY_ENDED);
        }
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("summary", summary);
        if (details != null) {
            snapshot.put("details", details);
        }
        this.status = AgentWorkStatus.COMPLETED;
        this.statusSource = StatusSource.MCP_REPORT;
        this.resultSnapshot = Map.copyOf(snapshot);
        this.lastSeenAt = now;
        this.completedAt = now;
    }

    public BoardLane boardLane() {
        return switch (status) {
            case WORKING -> BoardLane.WORKING;
            case WAITING -> BoardLane.WAITING;
            case COMPLETED -> BoardLane.COMPLETED;
            case FAILED, UNKNOWN -> BoardLane.ATTENTION;
        };
    }

    public String summary() {
        Map<String, Object> snapshot = switch (status) {
            case COMPLETED -> resultSnapshot;
            case FAILED -> errorSnapshot;
            case WORKING, WAITING, UNKNOWN -> progressSnapshot;
        };
        return snapshot == null ? null : (String) snapshot.get("summary");
    }
}
