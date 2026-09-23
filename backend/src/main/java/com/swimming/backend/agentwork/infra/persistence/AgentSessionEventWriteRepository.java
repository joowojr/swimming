package com.swimming.backend.agentwork.infra.persistence;

import com.swimming.backend.agentwork.infra.persistence.entity.AgentSessionEventEntity;
import org.springframework.data.repository.Repository;

public interface AgentSessionEventWriteRepository extends Repository<AgentSessionEventEntity, Long> {
    <S extends AgentSessionEventEntity> S saveAndFlush(S entity);
    void deleteAllInBatch();

}
