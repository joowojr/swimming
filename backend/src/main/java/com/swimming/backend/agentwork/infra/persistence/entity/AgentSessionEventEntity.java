package com.swimming.backend.agentwork.infra.persistence.entity;

import com.swimming.backend.agentwork.domain.AgentSession;
import com.swimming.backend.agentwork.domain.AgentSessionEventType;
import com.swimming.backend.agentwork.domain.AgentType;
import com.swimming.backend.agentwork.domain.StatusSource;
import com.swimming.backend.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "agent_session_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentSessionEventEntity extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_session_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_agent_session_events_session"))
    private AgentSessionEntity session;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_type", nullable = false, length = 30)
    private AgentType agentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private AgentSessionEventType eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusSource source;

    @Builder(access = AccessLevel.PRIVATE)
    private AgentSessionEventEntity(
            AgentSessionEntity session,
            AgentType agentType,
            AgentSessionEventType eventType,
            Map<String, Object> payload,
            StatusSource source
    ) {
        this.session = session;
        this.agentType = agentType;
        this.eventType = eventType;
        this.payload = payload;
        this.source = source;
    }

    public static AgentSessionEventEntity started(AgentSession domain, AgentSessionEntity reference) {
        return of(domain, reference, AgentSessionEventType.STARTED,
                Collections.singletonMap("instruction", domain.getInstruction()));
    }

    public static AgentSessionEventEntity completed(AgentSession domain, AgentSessionEntity reference) {
        return of(domain, reference, AgentSessionEventType.COMPLETED, domain.getResultSnapshot());
    }

    public static AgentSessionEventEntity workItemLinked(
            AgentSession domain, AgentSessionEntity reference, List<String> resourceIds) {
        return of(domain, reference, AgentSessionEventType.WORK_ITEM_LINKED, resourcePayload(resourceIds));
    }

    public static AgentSessionEventEntity workItemUnlinked(
            AgentSession domain, AgentSessionEntity reference, List<String> resourceIds) {
        return of(domain, reference, AgentSessionEventType.WORK_ITEM_UNLINKED, resourcePayload(resourceIds));
    }

    /** 이벤트마다 같은 자리에서 당시 Agent와 상태 출처를 세션에서 가져온다. */
    private static AgentSessionEventEntity of(
            AgentSession domain,
            AgentSessionEntity reference,
            AgentSessionEventType eventType,
            Map<String, Object> payload
    ) {
        return AgentSessionEventEntity.builder()
                .session(reference)
                .agentType(domain.getAgentType())
                .eventType(eventType)
                .payload(payload)
                .source(domain.getStatusSource())
                .build();
    }

    private static Map<String, Object> resourcePayload(List<String> resourceIds) {
        return Collections.singletonMap("resourceIds", List.copyOf(resourceIds));
    }
}

