package com.swimming.backend.session.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.session.domain.Session;
import com.swimming.backend.session.domain.SessionStatus;
import com.swimming.backend.session.domain.SessionTask;
import com.swimming.backend.session.dto.SessionWithPlace;
import com.swimming.backend.session.dto.projection.SessionListRow;
import com.swimming.backend.place.dto.projection.PlaceReferenceRow;
import com.swimming.backend.session.repository.entity.SessionEntity;
import com.swimming.backend.session.repository.SessionRepository;
import com.swimming.backend.session.repository.SessionTaskBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.List;
import java.util.LinkedHashMap;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;
    private final SessionTaskBatchRepository sessionTaskBatchRepository;

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public Session getOwned(Long userId, Long sessionId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .map(SessionEntity::toDomain)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public Optional<Session> getActive(Long userId) {
        return sessionRepository.findByUserIdAndStatus(
                userId,
                SessionStatus.IN_PROGRESS
        ).map(SessionEntity::toDomain);
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<SessionWithPlace> getOwnedSessionsWithPlaces(Long userId) {
        MapBuilder sessions = new MapBuilder();
        sessionRepository.findListRows(userId).forEach(sessions::add);
        return sessions.build();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Session create(Session session) {
        try {
            return sessionRepository.saveAndFlush(SessionEntity.from(session)).toDomain();
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.ACTIVE_SESSION_ALREADY_EXISTS, exception);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Session update(Session session) {
        int updatedCount = sessionRepository.updateOwnedSession(
                session.getId(),
                session.getUserId(),
                session.getStatus() == SessionStatus.IN_PROGRESS ? session.getUserId() : null,
                session.getMusicUrl(),
                session.getPlannedDurationSec(),
                session.getActualDurationSec(),
                session.getEndedAt(),
                session.getStatus(),
                session.getSummary()
        );
        if (updatedCount != 1) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        if (sessionTaskBatchRepository.updateCompletions(session.getId(), session.getTasks())
                != session.getTasks().size()) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        return session;
    }

    private static final class MapBuilder {
        private final LinkedHashMap<Long, List<SessionListRow>> rowsBySessionId = new LinkedHashMap<>();

        private void add(SessionListRow row) {
            rowsBySessionId.computeIfAbsent(row.sessionId(), key -> new java.util.ArrayList<>())
                    .add(row);
        }

        private List<SessionWithPlace> build() {
            return rowsBySessionId.values().stream()
                    .map(this::toSessionWithPlace)
                    .toList();
        }

        private SessionWithPlace toSessionWithPlace(List<SessionListRow> rows) {
            SessionListRow first = rows.getFirst();
            Session session = Session.restore(
                    first.sessionId(),
                    first.userId(),
                    first.type(),
                    first.placeId(),
                    rows.stream()
                            .map(row -> new SessionTask(row.taskId(), row.taskCompleted()))
                            .toList(),
                    first.musicUrl(),
                    first.plannedDurationSec(),
                    first.actualDurationSec(),
                    first.startedAt(),
                    first.endedAt(),
                    first.status(),
                    first.summary()
            );
            PlaceReferenceRow place = new PlaceReferenceRow(
                    first.placeId(),
                    first.cityId(),
                    first.cityName(),
                    first.placeName(),
                    first.backgroundAssetType(),
                    first.backgroundAssetKey(),
                    first.defaultMusicUrl()
            );
            return new SessionWithPlace(session, place);
        }
    }

}
