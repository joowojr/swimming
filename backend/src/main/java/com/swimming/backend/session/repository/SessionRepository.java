package com.swimming.backend.session.repository;

import com.swimming.backend.session.repository.entity.SessionEntity;
import com.swimming.backend.session.domain.SessionStatus;
import com.swimming.backend.session.dto.projection.SessionWithPlaceRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface SessionRepository extends JpaRepository<SessionEntity, Long> {

    Optional<SessionEntity> findByIdAndUserId(Long id, Long userId);

    @Query("""
            select new com.swimming.backend.session.dto.projection.SessionWithPlaceRow(
                session.id,
                session.userId,
                session.type,
                session.placeId,
                sessionTask.id.taskId,
                sessionTask.isCompleted,
                session.musicUrl,
                session.plannedDurationSec,
                session.focusDurationSec,
                session.breakDurationSec,
                session.repeatCount,
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
    List<SessionWithPlaceRow> findListRows(@Param("userId") Long userId);

    @Query("""
            select new com.swimming.backend.session.dto.projection.SessionWithPlaceRow(
                session.id,
                session.userId,
                session.type,
                session.placeId,
                sessionTask.id.taskId,
                sessionTask.isCompleted,
                session.musicUrl,
                session.plannedDurationSec,
                session.focusDurationSec,
                session.breakDurationSec,
                session.repeatCount,
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
              and session.status = :status
            order by sessionTask.id.taskId asc
            """)
    List<SessionWithPlaceRow> findActiveRows(
            @Param("userId") Long userId,
            @Param("status") SessionStatus status
    );

    @Query("""
            select new com.swimming.backend.session.dto.projection.SessionWithPlaceRow(
                session.id,
                session.userId,
                session.type,
                session.placeId,
                sessionTask.id.taskId,
                sessionTask.isCompleted,
                session.musicUrl,
                session.plannedDurationSec,
                session.focusDurationSec,
                session.breakDurationSec,
                session.repeatCount,
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
              and session.id = :sessionId
            order by sessionTask.id.taskId asc
            """)
    List<SessionWithPlaceRow> findOwnedRows(
            @Param("userId") Long userId,
            @Param("sessionId") Long sessionId
    );
}
