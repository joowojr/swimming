package com.swimming.backend.knowledge.repository.postgres;

import com.swimming.backend.knowledge.domain.SourceProcessingStatus;
import com.swimming.backend.knowledge.repository.postgres.entity.KnowledgeNodeEntity;
import com.swimming.backend.knowledge.repository.postgres.entity.KnowledgeSourceEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface KnowledgeSourceJpaRepository extends JpaRepository<KnowledgeSourceEntity, UUID> {

    interface SourceWithNode {

        KnowledgeSourceEntity getSource();

        KnowledgeNodeEntity getNode();
    }

    List<KnowledgeSourceEntity> findAllByNodeIdIn(Collection<UUID> nodeIds);

    /** 다른 도메인이 문서를 가리킬 때 쓴다. 존재·소유·삭제를 한 쿼리에서 판정한다. */
    @Query("""
            select s as source, n as node
              from KnowledgeSourceEntity s, KnowledgeNodeEntity n
             where s.nodeId = n.id
               and n.userId = :userId
               and n.deleted = false
               and s.nodeId in :nodeIds
            """)
    List<SourceWithNode> findAllActiveByIds(
            @Param("userId") Long userId,
            @Param("nodeIds") Collection<UUID> nodeIds
    );

    /** 위와 같되 폴더까지 좁힌다. 다른 폴더의 문서는 아예 돌아오지 않는다. */
    @Query("""
            select s as source, n as node
              from KnowledgeSourceEntity s, KnowledgeNodeEntity n
             where s.nodeId = n.id
               and n.userId = :userId
               and n.deleted = false
               and s.folderId = :folderId
               and s.nodeId in :nodeIds
            """)
    List<SourceWithNode> findAllActiveInFolderByIds(
            @Param("userId") Long userId,
            @Param("folderId") Long folderId,
            @Param("nodeIds") Collection<UUID> nodeIds
    );

    /**
     * Category 분류에 넣을 Source를 읽는다.
     *
     * <p>분류 대상은 이 Folder에 살아 있고 소화가 끝나 요약이 있는 내 문서다. 요청에
     * 섞인 다른 폴더·다른 사용자·지운 Source는 아예 돌아오지 않는다.
     *
     * <p>정렬은 노드 생성 시각 오름차순이다. 같은 입력이면 LLM에 같은 순서로 보내야
     * 두 번 실행한 결과를 견줄 수 있다.
     */
    @Query("""
            select s as source, n as node
              from KnowledgeSourceEntity s, KnowledgeNodeEntity n
             where s.nodeId = n.id
               and n.userId = :userId
               and n.deleted = false
               and s.folderId = :folderId
               and s.processingStatus = :status
               and s.summary is not null
               and s.nodeId in :nodeIds
             order by n.createdAt asc, s.nodeId asc
            """)
    List<SourceWithNode> findAllCategorizationTargetsInFolderByIds(
            @Param("userId") Long userId,
            @Param("folderId") Long folderId,
            @Param("status") SourceProcessingStatus status,
            @Param("nodeIds") Collection<UUID> nodeIds
    );

    @Query("""
            select s as source, n as node
              from KnowledgeSourceEntity s, KnowledgeNodeEntity n
             where s.nodeId = n.id
               and n.userId = :userId
               and n.deleted = false
               and s.folderId = :folderId
               and s.canonicalUrl in :canonicalUrls
            """)
    List<SourceWithNode> findAllActiveInFolderByCanonicalUrls(
            @Param("userId") Long userId,
            @Param("folderId") Long folderId,
            @Param("canonicalUrls") Collection<String> canonicalUrls
    );

    /** 이 Folder에 살아 있는 Source의 node id만. */
    @Query("""
            select s.nodeId
              from KnowledgeSourceEntity s, KnowledgeNodeEntity n
             where s.nodeId = n.id
               and n.userId = :userId
               and n.deleted = false
               and s.folderId = :folderId
            """)
    List<UUID> findAliveNodeIdsInFolder(
            @Param("userId") Long userId,
            @Param("folderId") Long folderId
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update knowledge_source s
               set summary_embedding = cast(:summaryEmbedding as vector),
                   summary_embedding_model = :embeddingModel,
                   updated_at = current_timestamp
             where s.node_id = :sourceId
               and exists (
                    select 1
                      from knowledge_node n
                     where n.id = s.node_id
                       and n.user_id = :userId
                       and n.is_deleted = false
               )
            """, nativeQuery = true)
    int updateSummaryEmbedding(
            @Param("userId") Long userId,
            @Param("sourceId") UUID sourceId,
            @Param("summaryEmbedding") String summaryEmbedding,
            @Param("embeddingModel") String embeddingModel
    );

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

    @Query("""
            select s
            from KnowledgeSourceEntity s, KnowledgeNodeEntity n
            where s.nodeId = n.id
              and n.userId = :userId
              and n.deleted = false
              and (:filterFolder = false or s.folderId = :folderId)
              and (:filterSources = false or s.nodeId in :sourceIds)
            order by n.createdAt desc, s.nodeId desc
            """)
    List<KnowledgeSourceEntity> findFirstSearchPage(
            @Param("userId") Long userId,
            @Param("filterFolder") boolean filterFolder,
            @Param("folderId") Long folderId,
            @Param("filterSources") boolean filterSources,
            @Param("sourceIds") Collection<UUID> sourceIds,
            Pageable pageable
    );

    @Query("""
            select s
            from KnowledgeSourceEntity s, KnowledgeNodeEntity n
            where s.nodeId = n.id
              and n.userId = :userId
              and n.deleted = false
              and (:filterFolder = false or s.folderId = :folderId)
              and (:filterSources = false or s.nodeId in :sourceIds)
              and (
                    n.createdAt < :cursorCreatedAt
                 or (n.createdAt = :cursorCreatedAt and s.nodeId < :cursorNodeId)
              )
            order by n.createdAt desc, s.nodeId desc
            """)
    List<KnowledgeSourceEntity> findNextSearchPage(
            @Param("userId") Long userId,
            @Param("filterFolder") boolean filterFolder,
            @Param("folderId") Long folderId,
            @Param("filterSources") boolean filterSources,
            @Param("sourceIds") Collection<UUID> sourceIds,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorNodeId") UUID cursorNodeId,
            Pageable pageable
    );
}
