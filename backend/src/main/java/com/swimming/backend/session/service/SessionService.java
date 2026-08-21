package com.swimming.backend.session.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.session.domain.Session;
import com.swimming.backend.session.domain.SessionStatus;
import com.swimming.backend.session.repository.entity.SessionEntity;
import com.swimming.backend.session.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;

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

    @Transactional(propagation = Propagation.REQUIRED)
    public Session save(Session session) {
        if (session.getId() == null) {
            return saveNew(session);
        }

        SessionEntity entity = sessionRepository
                .findByIdAndUserId(session.getId(), session.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        entity.apply(session);
        return sessionRepository.saveAndFlush(entity).toDomain();
    }

    private Session saveNew(Session session) {
        if (sessionRepository.existsByUserIdAndStatus(
                session.getUserId(),
                SessionStatus.IN_PROGRESS
        )) {
            throw new BusinessException(ErrorCode.ACTIVE_SESSION_ALREADY_EXISTS);
        }

        try {
            return sessionRepository.saveAndFlush(SessionEntity.from(session)).toDomain();
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.ACTIVE_SESSION_ALREADY_EXISTS, exception);
        }
    }
}
