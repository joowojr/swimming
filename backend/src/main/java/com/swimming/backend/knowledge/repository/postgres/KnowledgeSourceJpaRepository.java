package com.swimming.backend.knowledge.repository.postgres;

import com.swimming.backend.knowledge.repository.postgres.entity.KnowledgeSourceEntity;
import com.swimming.backend.knowledge.domain.SourceProcessingStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface KnowledgeSourceJpaRepository extends JpaRepository<KnowledgeSourceEntity, UUID> {

    List<KnowledgeSourceEntity> findAllByNodeIdIn(Collection<UUID> nodeIds);

    List<KnowledgeSourceEntity> findAllByCanonicalUrl(String canonicalUrl);

    /**
     * 정렬 기준을 노드의 생성 시각으로 잡는다. 커서에 싣는 값과 같은 것을 써야 경계에서
     * 행이 겹치거나 빠지지 않는다.
     *
     * <p>다음 페이지를 같은 쿼리로 합치지 않는다. {@code :cursor is null} 로 묶으면 그
     * 파라미터가 비교 없이 홀로 놓여 PostgreSQL이 타입을 정하지 못한다
     * ({@code could not determine data type of parameter}).
     */
    @Query("""
            select s
            from KnowledgeSourceEntity s, KnowledgeNodeEntity n
            where s.nodeId = n.id
              and n.userId = :userId
              and n.deleted = false
              and s.folderId = :folderId
              and (:status is null or s.processingStatus = :status)
            order by n.createdAt desc, s.nodeId desc
            """)
    List<KnowledgeSourceEntity> findFirstPage(
            @Param("userId") Long userId,
            @Param("folderId") Long folderId,
            @Param("status") SourceProcessingStatus status,
            Pageable pageable
    );

    @Query("""
            select s
            from KnowledgeSourceEntity s, KnowledgeNodeEntity n
            where s.nodeId = n.id
              and n.userId = :userId
              and n.deleted = false
              and s.folderId = :folderId
              and (:status is null or s.processingStatus = :status)
              and (
                    n.createdAt < :cursorCreatedAt
                 or (n.createdAt = :cursorCreatedAt and s.nodeId < :cursorNodeId)
              )
            order by n.createdAt desc, s.nodeId desc
            """)
    List<KnowledgeSourceEntity> findNextPage(
            @Param("userId") Long userId,
            @Param("folderId") Long folderId,
            @Param("status") SourceProcessingStatus status,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorNodeId") UUID cursorNodeId,
            Pageable pageable
    );
}
