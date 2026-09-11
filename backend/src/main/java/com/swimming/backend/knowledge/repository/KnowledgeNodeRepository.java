package com.swimming.backend.knowledge.repository;

import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.NodeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 저장소 구현과 무관한 KnowledgeNode 조회·저장 계약.
 * 반환되는 도메인 객체는 저장소에서 분리되어 있으므로 변경은 반드시 save로 반영한다.
 */
public interface KnowledgeNodeRepository {

    KnowledgeNode save(KnowledgeNode node);

    Optional<KnowledgeNode> findById(UUID id);

    Optional<KnowledgeNode> findByIdAndUserId(UUID id, Long userId);

    List<KnowledgeNode> findAllByIds(Collection<UUID> ids);

    List<KnowledgeNode> findAllByUserIdAndNodeType(Long userId, NodeType nodeType);

    /**
     * Subject / Topic resolution의 매칭 단계에서 사용한다.
     * 인자는 {@link com.swimming.backend.knowledge.domain.NodeTitleNormalizer}를 거친 값이다.
     */
    Optional<KnowledgeNode> findByUserIdAndNodeTypeAndNormalizedTitle(
            Long userId,
            NodeType nodeType,
            String normalizedTitle
    );

    KnowledgeNode createSubjectWithEmbedding(
            KnowledgeNode subject,
            float[] titleEmbedding,
            String embeddingModel
    );

    List<KnowledgeNode> findSimilarSubjects(
            Long userId,
            float[] titleEmbedding,
            String embeddingModel,
            int limit
    );

    void deleteById(UUID id);
}
