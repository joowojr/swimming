package com.swimming.backend.agentwork.repository;

import com.swimming.backend.agentwork.repository.entity.AgentSessionEventEntity;
import org.springframework.data.repository.Repository;

public interface AgentSessionEventWriteRepository extends Repository<AgentSessionEventEntity, Long> {
    <S extends AgentSessionEventEntity> S saveAndFlush(S entity);
    void deleteAllInBatch();

}
