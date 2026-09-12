package com.swimming.backend.knowledge.repository;

import com.swimming.backend.knowledge.domain.KnowledgeNode;

import java.util.List;
import java.util.UUID;

/** Knowledge embedding의 유사도 검색만 담당한다. */
public interface KnowledgeVectorSearchRepository {

    List<KnowledgeNode> findSimilarSubjects(
            Long userId,
            float[] titleEmbedding,
            String embeddingModel,
            int limit
    );

    List<SimilarSource> findSimilarSources(
            Long userId,
            UUID excludedSourceId,
            float[] summaryEmbedding,
            String embeddingModel,
            int limit
    );
}
