package com.swimming.backend.agentwork.infra.persistence.entity;

import com.swimming.backend.agentwork.domain.WorkResourceType;
import com.swimming.backend.common.entity.BaseTimeEntity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "agent_work_items", uniqueConstraints =
        @UniqueConstraint(name = "uk_agent_work_items_resource", columnNames = {"user_id", "resource_type", "resource_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentWorkItemEntity extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 30)
    private WorkResourceType resourceType;

    @Column(name = "resource_id", nullable = false, length = 100)
    private String resourceId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_session_id", foreignKey = @ForeignKey(name = "fk_agent_work_items_session"))
    private AgentSessionEntity session;

}
