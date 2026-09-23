package com.swimming.backend.agentwork.repository;

import com.swimming.backend.agentwork.repository.entity.AgentSessionEntity;
import org.springframework.data.repository.Repository;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface AgentSessionWriteRepository extends Repository<AgentSessionEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AgentSessionEntity> findByIdAndUserId(Long id, Long userId);
    <S extends AgentSessionEntity> S saveAndFlush(S entity);
    void deleteAllInBatch();

}
