package com.swimming.backend.session.repository.entity;

import com.swimming.backend.common.entity.BaseTimeEntity;
import com.swimming.backend.session.domain.Session;
import com.swimming.backend.session.domain.SessionStatus;
import com.swimming.backend.session.domain.SessionType;
import jakarta.persistence.*;
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

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id.taskId ASC")
    private List<SessionTaskEntity> tasks = new ArrayList<>();

    @Column(name = "music_url", length = 2048)
    private String musicUrl;

    @Column(name = "planned_duration_sec", nullable = false)
    private int plannedDurationSec;

    @Column(name = "focus_duration_sec", nullable = false)
    private int focusDurationSec;

    @Column(name = "break_duration_sec", nullable = false)
    private int breakDurationSec;

    @Column(name = "repeat_count", nullable = false)
    private int repeatCount;

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
        session.getTasks().forEach(task -> this.tasks.add(SessionTaskEntity.from(this, task)));
        this.musicUrl = session.getMusicUrl();
        this.plannedDurationSec = session.getPlannedDurationSec();
        this.focusDurationSec = session.getFocusDurationSec();
        this.breakDurationSec = session.getBreakDurationSec();
        this.repeatCount = session.getRepeatCount();
        this.actualDurationSec = session.getActualDurationSec();
        this.startedAt = session.getStartedAt();
        this.endedAt = session.getEndedAt();
        this.status = session.getStatus();
        this.summary = session.getSummary();
    }

    public static SessionEntity from(Session session) {
        return new SessionEntity(session);
    }

    public void end(Session session) {
        this.activeUserId = null;
        this.actualDurationSec = session.getActualDurationSec();
        this.endedAt = session.getEndedAt();
        this.status = session.getStatus();
        this.summary = session.getSummary();
    }

    public void updateMusicUrl(String musicUrl) {
        this.musicUrl = musicUrl;
    }

    public void updateFocusDuration(int focusDurationSec, int plannedDurationSec) {
        this.focusDurationSec = focusDurationSec;
        this.plannedDurationSec = plannedDurationSec;
    }

    public Session toDomain() {
        return Session.restore(
                id,
                userId,
                type,
                placeId,
                tasks.stream().map(SessionTaskEntity::toDomain).toList(),
                musicUrl,
                plannedDurationSec,
                focusDurationSec,
                breakDurationSec,
                repeatCount,
                actualDurationSec,
                startedAt,
                endedAt,
                status,
                summary
        );
    }
}
