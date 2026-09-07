package com.swimming.backend.knowledge.repository;

import com.swimming.backend.knowledge.domain.KnowledgeSource;

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

    /**
     * 같은 문서를 다시 저장했는지 확인한다.
     */
    Optional<KnowledgeSource> findByUserIdAndCanonicalUrl(Long userId, String canonicalUrl);

    /**
     * Folder 안의 Source를 최근 순으로 읽는다.
     *
     * @return 요청한 {@code limit}만큼. 다음 페이지가 있는지는 호출하는 쪽이
     *         {@code limit + 1}을 요청해 판단한다
     */
    List<KnowledgeSource> findPage(SourcePageQuery query);
}
