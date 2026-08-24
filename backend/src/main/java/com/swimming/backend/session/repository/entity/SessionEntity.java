package com.swimming.backend.session.repository.entity;

import com.swimming.backend.common.entity.BaseTimeEntity;
import com.swimming.backend.session.domain.Session;
import com.swimming.backend.session.domain.SessionStatus;
import com.swimming.backend.session.domain.SessionType;
import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "sessions",
        uniqueConstraints = @UniqueConstraint(
                name = "sessions_active_user_unique",
                columnNames = "active_user_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "active_user_id")
    private Long activeUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionType type;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @ElementCollection
    @CollectionTable(
            name = "session_tasks",
            joinColumns = @JoinColumn(name = "session_id")
    )
    @OrderColumn(name = "order_idx")
    private List<SessionTaskEmbeddable> tasks = new ArrayList<>();

    @Column(name = "music_url", length = 2048)
    private String musicUrl;

    @Column(name = "planned_duration_sec", nullable = false)
    private int plannedDurationSec;

    @Column(name = "actual_duration_sec")
    private Integer actualDurationSec;

    @Column(name = "started_at", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Generated(event = EventType.INSERT)
    private Instant startedAt;

    @Column(name = "end_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    @Column(length = 255)
    private String summary;

    private SessionEntity(Session session) {
        this.userId = session.getUserId();
        this.activeUserId = session.getStatus() == SessionStatus.IN_PROGRESS
                ? session.getUserId()
                : null;
        this.type = session.getType();
        this.placeId = session.getPlaceId();
        session.getTasks().forEach(task -> this.tasks.add(SessionTaskEmbeddable.from(task)));
        this.musicUrl = session.getMusicUrl();
        this.plannedDurationSec = session.getPlannedDurationSec();
        this.actualDurationSec = session.getActualDurationSec();
        this.startedAt = session.getStartedAt();
        this.endedAt = session.getEndedAt();
        this.status = session.getStatus();
        this.summary = session.getSummary();
    }

    public static SessionEntity from(Session session) {
        return new SessionEntity(session);
    }

    public void apply(Session session) {
        plannedDurationSec = session.getPlannedDurationSec();
        actualDurationSec = session.getActualDurationSec();
        endedAt = session.getEndedAt();
        status = session.getStatus();
        musicUrl = session.getMusicUrl();
        activeUserId = status == SessionStatus.IN_PROGRESS ? userId : null;
        summary = session.getSummary();
    }

    public Session toDomain() {
        return Session.restore(
                id,
                userId,
                type,
                placeId,
                tasks.stream().map(SessionTaskEmbeddable::toDomain).toList(),
                musicUrl,
                plannedDurationSec,
                actualDurationSec,
                startedAt,
                endedAt,
                status,
                summary
        );
    }
}
