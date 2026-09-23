package com.swimming.backend.agentwork.repository;

import com.swimming.backend.agentwork.repository.entity.AgentWorkItemEntity;
import org.springframework.data.repository.Repository;
import java.util.List;
import java.util.Optional;
import com.swimming.backend.agentwork.domain.WorkResourceType;
import com.swimming.backend.agentwork.repository.entity.AgentSessionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

public interface AgentWorkItemWriteRepository extends Repository<AgentWorkItemEntity, Long> {
    /** 최초 등록 경합도 트랜잭션 실패 없이 처리하고 같은 카드의 잠금을 획득한다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO agent_work_items (user_id, resource_type, resource_id, created_at, updated_at)
            VALUES (:userId, :resourceType, :resourceId, :now, :now)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("resourceType") String resourceType,
                       @Param("resourceId") String resourceId, @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT item FROM AgentWorkItemEntity item
            WHERE item.userId = :userId AND item.resourceType = :resourceType AND item.resourceId = :resourceId
            """)
    Optional<AgentWorkItemEntity> findOwnedResourceForUpdate(@Param("userId") Long userId,
            @Param("resourceType") WorkResourceType resourceType, @Param("resourceId") String resourceId);
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE AgentWorkItemEntity item
            SET item.session = :session, item.updatedAt = :updatedAt
            WHERE item.userId = :userId AND item.id IN :workItemIds
              AND (item.session IS NULL OR item.session.id = :sessionId)
            """)
    int attachSession(@Param("userId") Long userId, @Param("workItemIds") List<Long> workItemIds,
                      @Param("sessionId") Long sessionId, @Param("session") AgentSessionEntity session,
                      @Param("updatedAt") Instant updatedAt);
    void deleteAllInBatch();

}
