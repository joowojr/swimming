package com.swimming.backend.agentwork.infra.persistence.entity;

import com.swimming.backend.agentwork.domain.AgentSession;
import com.swimming.backend.agentwork.domain.AgentType;
import com.swimming.backend.agentwork.domain.AgentWorkStatus;
import com.swimming.backend.agentwork.domain.StatusSource;
import com.swimming.backend.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "agent_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentSessionEntity extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_type", nullable = false, length = 30)
    private AgentType agentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgentWorkStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_source", nullable = false, length = 30)
    private StatusSource statusSource;

    @Column(length = 2000)
    private String instruction;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "progress_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> progressSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> resultSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> errorSnapshot;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private AgentSessionEntity(
            Long userId,
            AgentType agentType,
            AgentWorkStatus status,
            StatusSource statusSource,
            String instruction,
            Map<String, Object> progressSnapshot,
            Map<String, Object> resultSnapshot,
            Map<String, Object> errorSnapshot,
            Instant startedAt,
            Instant lastSeenAt,
            Instant completedAt
    ) {
        this.userId = userId;
        this.agentType = agentType;
        this.status = status;
        this.statusSource = statusSource;
        this.instruction = instruction;
        this.progressSnapshot = progressSnapshot;
        this.resultSnapshot = resultSnapshot;
        this.errorSnapshot = errorSnapshot;
        this.startedAt = startedAt;
        this.lastSeenAt = lastSeenAt;
        this.completedAt = completedAt;
    }

    public static AgentSessionEntity from(AgentSession session) {
        return AgentSessionEntity.builder()
                .userId(session.getUserId())
                .agentType(session.getAgentType())
                .status(session.getStatus())
                .statusSource(session.getStatusSource())
                .instruction(session.getInstruction())
                .progressSnapshot(session.getProgressSnapshot())
                .resultSnapshot(session.getResultSnapshot())
                .errorSnapshot(session.getErrorSnapshot())
                .startedAt(session.getStartedAt())
                .lastSeenAt(session.getLastSeenAt())
                .completedAt(session.getCompletedAt())
                .build();
    }

    public void apply(AgentSession session) {
        this.agentType = session.getAgentType();
        this.status = session.getStatus();
        this.statusSource = session.getStatusSource();
        this.instruction = session.getInstruction();
        this.progressSnapshot = session.getProgressSnapshot();
        this.resultSnapshot = session.getResultSnapshot();
        this.errorSnapshot = session.getErrorSnapshot();
        this.startedAt = session.getStartedAt();
        this.lastSeenAt = session.getLastSeenAt();
        this.completedAt = session.getCompletedAt();
    }

    public AgentSession toDomain() {
        return AgentSession.restore(id, userId, agentType, status, statusSource,
                instruction, progressSnapshot, resultSnapshot, errorSnapshot, startedAt, lastSeenAt, completedAt);
    }
}
