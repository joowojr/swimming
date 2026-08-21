package com.swimming.backend.session.repository;

import com.swimming.backend.session.repository.entity.SessionEntity;
import com.swimming.backend.session.domain.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SessionRepository extends JpaRepository<SessionEntity, Long> {

    boolean existsByUserIdAndStatus(Long userId, SessionStatus status);

    Optional<SessionEntity> findByIdAndUserId(Long id, Long userId);

    Optional<SessionEntity> findByUserIdAndStatus(Long userId, SessionStatus status);
}
