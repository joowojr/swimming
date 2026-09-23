package com.swimming.backend.agentwork.infra.persistence;

import com.swimming.backend.agentwork.infra.persistence.entity.AgentSessionEntity;
import org.springframework.data.repository.Repository;
import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface AgentSessionReadRepository extends Repository<AgentSessionEntity, Long> {
    List<AgentSessionEntity> findAllByUserIdAndIdIn(Long userId, Collection<Long> ids);
    Optional<AgentSessionEntity> findById(Long id);
    long count();

}
