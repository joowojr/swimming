package com.swimming.backend.session.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.session.domain.Session;
import com.swimming.backend.session.domain.SessionStatus;
import com.swimming.backend.session.dto.projection.SessionWithPlaceRow;
import com.swimming.backend.session.repository.entity.SessionEntity;
import com.swimming.backend.session.repository.SessionRepository;
import com.swimming.backend.session.repository.SessionTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;
    private final SessionTaskRepository sessionTaskRepository;

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public Session getOwned(Long userId, Long sessionId) {
        return getOwnedEntity(userId, sessionId).toDomain();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<SessionWithPlaceRow> getActiveRows(Long userId) {
        return sessionRepository.findActiveRows(
                userId,
                SessionStatus.IN_PROGRESS
        );
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<SessionWithPlaceRow> getOwnedRows(Long userId, Long sessionId) {
        List<SessionWithPlaceRow> rows = sessionRepository.findOwnedRows(userId, sessionId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        return rows;
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<SessionWithPlaceRow> getOwnedRows(Long userId) {
        return sessionRepository.findListRows(userId);
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
    public Session updateEnd(
            Session session,
            List<Long> completedTaskIds
    ) {
        SessionEntity entity = sessionRepository.findById(session.getId())
                .filter(candidate -> candidate.getUserId().equals(session.getUserId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        entity.end(session);
        if (!completedTaskIds.isEmpty()
                && sessionTaskRepository.completeAll(session.getId(), completedTaskIds)
                != completedTaskIds.size()) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        return session;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updateMusicUrl(Long userId, Long sessionId, String musicUrl) {
        SessionEntity entity = getOwnedEntity(userId, sessionId);
        validateInProgress(entity);
        entity.updateMusicUrl(musicUrl);
        sessionRepository.flush();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updateFocusDuration(Session session) {
        SessionEntity entity = getOwnedEntity(session.getUserId(), session.getId());
        validateInProgress(entity);
        entity.updateFocusDuration(
                session.getFocusDurationSec(),
                session.getPlannedDurationSec()
        );
        sessionRepository.flush();
    }

    private void validateInProgress(SessionEntity entity) {
        if (entity.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
    }

    private SessionEntity getOwnedEntity(Long userId, Long sessionId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
    }

}
