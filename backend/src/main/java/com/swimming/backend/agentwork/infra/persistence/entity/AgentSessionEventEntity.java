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
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Collections;
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

    public static AgentSessionEventEntity started(AgentSession domain, AgentSessionEntity reference) {
        AgentSessionEventEntity event = new AgentSessionEventEntity();
        event.session = reference;
        event.agentType = domain.getAgentType();
        event.eventType = AgentSessionEventType.STARTED;
        event.payload = Collections.singletonMap("instruction", domain.getInstruction());
        event.source = domain.getStatusSource();
        return event;
    }
    public static AgentSessionEventEntity completed(AgentSession domain, AgentSessionEntity reference) {
        AgentSessionEventEntity event = new AgentSessionEventEntity();
        event.session = reference;
        event.agentType = domain.getAgentType();
        event.eventType = AgentSessionEventType.COMPLETED;
        event.payload = domain.getResultSnapshot();
        event.source = domain.getStatusSource();
        return event;
    }
}

