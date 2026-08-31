package com.swimming.backend.session.repository;

import com.swimming.backend.session.repository.entity.SessionEntity;
import com.swimming.backend.session.domain.SessionStatus;
import com.swimming.backend.session.dto.projection.SessionListRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface SessionRepository extends JpaRepository<SessionEntity, Long> {

    Optional<SessionEntity> findByIdAndUserId(Long id, Long userId);

    Optional<SessionEntity> findByUserIdAndStatus(Long userId, SessionStatus status);

    @Query("""
            select new com.swimming.backend.session.dto.projection.SessionListRow(
                session.id,
                session.userId,
                session.type,
                session.placeId,
                sessionTask.id.taskId,
                sessionTask.isCompleted,
                session.musicUrl,
                session.plannedDurationSec,
                session.actualDurationSec,
                session.startedAt,
                session.endedAt,
                session.status,
                session.summary,
                city.id,
                city.name,
                city.countryCode,
                city.timezone,
                place.name,
                place.backgroundAssetType,
                place.backgroundAssetKey,
                place.defaultMusicUrl
            )
            from SessionEntity session
            join session.tasks sessionTask
            join PlaceEntity place on place.id = session.placeId
            join place.city city
            where session.userId = :userId
            order by session.startedAt desc, sessionTask.id.taskId asc
            """)
    List<SessionListRow> findListRows(@Param("userId") Long userId);
}
