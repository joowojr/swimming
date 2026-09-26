package com.swimming.backend.agentwork.infra.persistence;

import com.swimming.backend.agentwork.infra.persistence.entity.AgentWorkItemEntity;
import org.springframework.data.repository.Repository;
import java.util.List;
import java.util.Optional;
import com.swimming.backend.agentwork.application.dto.AgentWorkItemRow;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentWorkItemReadRepository extends Repository<AgentWorkItemEntity, Long> {
    @Query("""
            SELECT new com.swimming.backend.agentwork.application.dto.AgentWorkItemRow(
                item.id, item.resourceType, item.resourceId, session.id, item.createdAt)
            FROM AgentWorkItemEntity item LEFT JOIN item.session session
            WHERE item.userId = :userId
            ORDER BY item.id
            """)
    List<AgentWorkItemRow> findBoardItems(@Param("userId") Long userId);

    @Query("SELECT item.session.id FROM AgentWorkItemEntity item WHERE item.userId = :userId AND item.id = :workItemId")
    Optional<Long> findSessionId(@Param("userId") Long userId, @Param("workItemId") Long workItemId);

    @Query("SELECT item.id FROM AgentWorkItemEntity item WHERE item.userId = :userId AND item.session.id = :sessionId ORDER BY item.id")
    List<Long> findIdsBySession(@Param("userId") Long userId, @Param("sessionId") Long sessionId);
    Optional<AgentWorkItemEntity> findById(Long id);
    List<AgentWorkItemEntity> findAll();
    long count();

}
