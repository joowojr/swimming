package com.swimming.backend.knowledge.repository;

import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.SourceProcessingStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SOURCE 노드와 문서 데이터를 하나의 애그리거트로 저장한다.
 * 반환되는 도메인 객체는 저장소에서 분리되어 있으므로 변경은 반드시 save로 반영한다.
 */
public interface KnowledgeSourceRepository {

    KnowledgeSource save(KnowledgeSource source);

    Optional<KnowledgeSource> findById(UUID nodeId);

    List<KnowledgeSource> findAllByIds(Collection<UUID> nodeIds);

    /** 살아 있는 내 문서만. 존재·소유·삭제를 한 쿼리에서 판정한다. */
    List<KnowledgeSource> findAllActiveByIds(Long userId, Collection<UUID> nodeIds);

    /** 위와 같되 폴더까지 좁힌다. */
    List<KnowledgeSource> findAllActiveInFolderByIds(Long userId, Long folderId, Collection<UUID> nodeIds);

    /**
     * 이 Folder에 살아 있는 Source의 node id.
     *
     * <p>Category soft delete 대상을 고를 때, 그 Source로 들어오는 {@code CONTAINS}를
     * 찾기 위한 입력이 된다.
     */
    List<UUID> findAliveNodeIdsInFolder(Long userId, Long folderId);

    /**
     * Category 분류에 넣을 수 있는 Source를 이 Folder에서 읽는다.
     *
     * <p>분류 대상은 살아 있고 소화가 끝나 요약이 있는 Source다. 오래된 것부터 돌려준다.
     */
    List<KnowledgeSource> findAllCategorizationTargets(
            Long userId,
            Long folderId,
            Collection<UUID> nodeIds
    );

    void saveSummaryEmbedding(
            Long userId,
            UUID sourceId,
            float[] summaryEmbedding,
            String embeddingModel
    );

    /**
     * 이 Folder에 같은 문서를 다시 저장했는지 확인한다.
     *
     * <p>Folder 밖은 보지 않는다. 사용자는 폴더 단위로 링크를 모으므로, 다른 폴더에 있는
     * 같은 문서는 이 폴더에서 보면 없는 것이다.
     */
    List<KnowledgeSource> findAllInFolderByCanonicalUrls(
            Long userId,
            Long folderId,
            Collection<String> canonicalUrls
    );

    /**
     * Folder 안의 Source를 최근 순으로 읽는다.
     *
     * @return 요청한 {@code limit}만큼. 다음 페이지가 있는지는 호출하는 쪽이
     *         {@code limit + 1}을 요청해 판단한다
     */
    List<KnowledgeSource> findPage(SourcePageQuery query);

    /** 사용자 전체 또는 선택한 Folder에서 관계 조건에 맞는 Source 후보를 최근 순으로 읽는다. */
    List<KnowledgeSource> findSearchPage(SourceSearchPageQuery query);

    void updateReadAt(UUID sourceId, Instant readAt);

    void updateStatus(
            UUID sourceId,
            SourceProcessingStatus status,
            String failureMessage,
            boolean retryable
    );
}
