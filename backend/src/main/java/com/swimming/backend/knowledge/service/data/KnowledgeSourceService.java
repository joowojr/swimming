package com.swimming.backend.knowledge.service.data;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.repository.KnowledgeSourceRepository;
import com.swimming.backend.knowledge.repository.SourcePageQuery;
import com.swimming.backend.knowledge.repository.SourceSearchPageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KnowledgeSourceService {

    private final KnowledgeSourceRepository sourceRepository;

    @Transactional(propagation = Propagation.REQUIRED)
    public KnowledgeSource save(KnowledgeSource source) {
        return sourceRepository.save(source);
    }

    /** 읽음으로 표시한다. 시각은 부르는 쪽이 서버 시계에서 뽑아 넘긴다. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void markRead(KnowledgeSource source) {
        sourceRepository.updateReadAt(source.getId(), source.getReadAt());
    }

    /** 읽음 표시를 되돌린다. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void markUnread(KnowledgeSource source) {
        sourceRepository.updateReadAt(source.getId(), null);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updateStatus(KnowledgeSource source) {
        sourceRepository.updateStatus(
                source.getId(),
                source.getProcessingStatus(),
                source.getFailureMessage(),
                source.isRetryable()
        );
    }

    /** 원문은 남기고 노드만 지운 것으로 표시한다. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void delete(KnowledgeSource source) {
        source.delete();
        sourceRepository.save(source);
    }

    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public KnowledgeSource getOwned(UUID sourceId, Long userId) {
        return sourceRepository.findById(sourceId)
                .filter(source -> source.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_NODE_NOT_FOUND));
    }

    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public List<KnowledgeSource> findAllInFolderByCanonicalUrls(
            Long userId,
            Long folderId,
            Collection<String> canonicalUrls
    ) {
        return sourceRepository.findAllInFolderByCanonicalUrls(userId, folderId, canonicalUrls);
    }

    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public List<KnowledgeSource> findAllByIds(Collection<UUID> sourceIds) {
        return sourceRepository.findAllByIds(sourceIds);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void saveSummaryEmbedding(
            Long userId,
            UUID sourceId,
            float[] summaryEmbedding,
            String embeddingModel
    ) {
        sourceRepository.saveSummaryEmbedding(
                userId, sourceId, summaryEmbedding, embeddingModel
        );
    }

    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public boolean existsInFolder(Long userId, Long folderId) {
        return sourceRepository.existsInFolder(userId, folderId);
    }

    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public List<KnowledgeSource> findPage(SourcePageQuery query) {
        return sourceRepository.findPage(query);
    }

    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public List<KnowledgeSource> findSearchPage(SourceSearchPageQuery query) {
        if (query.sourceIds() != null && query.sourceIds().isEmpty()) {
            return List.of();
        }
        return sourceRepository.findSearchPage(query);
    }
}
