package com.swimming.backend.knowledge.service.data;

import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.repository.KnowledgeVectorSearchRepository;
import com.swimming.backend.knowledge.repository.SimilarSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KnowledgeVectorSearchService {

    private final KnowledgeVectorSearchRepository vectorSearchRepository;

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<KnowledgeNode> findSimilarSubjects(
            Long userId,
            float[] titleEmbedding,
            String embeddingModel,
            int limit
    ) {
        return vectorSearchRepository.findSimilarSubjects(
                userId, titleEmbedding, embeddingModel, limit);
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<SimilarSource> findSimilarSources(
            Long userId,
            UUID excludedSourceId,
            float[] summaryEmbedding,
            String embeddingModel,
            int limit
    ) {
        return vectorSearchRepository.findSimilarSources(
                userId, excludedSourceId, summaryEmbedding, embeddingModel, limit);
    }
}
