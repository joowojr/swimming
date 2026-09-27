package com.swimming.backend.agentwork.infra.persistence;

import com.swimming.backend.agentwork.infra.persistence.entity.AgentSessionEventEntity;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface AgentSessionEventReadRepository extends Repository<AgentSessionEventEntity, Long> {
    List<AgentSessionEventEntity> findAll();
    @Query("""
            SELECT event FROM AgentSessionEventEntity event
            WHERE event.session.id = :sessionId AND event.session.userId = :userId
            ORDER BY event.createdAt ASC, event.id ASC
            """)
    List<AgentSessionEventEntity> findAllOwnedBySession(@Param("userId") Long userId,
                                                         @Param("sessionId") Long sessionId);
    long count();

}
