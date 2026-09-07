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
     * 이 Folder에 같은 문서를 다시 저장했는지 확인한다.
     *
     * <p>Folder 밖은 보지 않는다. 사용자는 폴더 단위로 링크를 모으므로, 다른 폴더에 있는
     * 같은 문서는 이 폴더에서 보면 없는 것이다.
     */
    Optional<KnowledgeSource> findInFolderByCanonicalUrl(Long userId, Long folderId, String canonicalUrl);

    /**
     * Folder에 살아 있는 Source가 하나라도 있는지.
     *
     * <p>폴더 삭제 가드가 쓰는 {@code folders.has_source}를 정하는 값이다. 개수는 필요
     * 없으므로 세지 않는다.
     */
    boolean existsInFolder(Long userId, Long folderId);

    /**
     * Folder 안의 Source를 최근 순으로 읽는다.
     *
     * @return 요청한 {@code limit}만큼. 다음 페이지가 있는지는 호출하는 쪽이
     *         {@code limit + 1}을 요청해 판단한다
     */
    List<KnowledgeSource> findPage(SourcePageQuery query);

    /** 사용자 전체 또는 선택한 Folder에서 관계 조건에 맞는 Source 후보를 최근 순으로 읽는다. */
    List<KnowledgeSource> findSearchPage(SourceSearchPageQuery query);
}
