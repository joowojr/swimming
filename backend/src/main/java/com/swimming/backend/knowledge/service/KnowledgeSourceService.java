package com.swimming.backend.knowledge.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.repository.KnowledgeSourceRepository;
import com.swimming.backend.knowledge.repository.SourcePageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KnowledgeSourceService {

    private final KnowledgeSourceRepository sourceRepository;

    @Transactional(propagation = Propagation.REQUIRED)
    public KnowledgeSource save(KnowledgeSource source) {
        return sourceRepository.save(source);
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
    public Optional<KnowledgeSource> findByCanonicalUrl(Long userId, String canonicalUrl) {
        return sourceRepository.findByUserIdAndCanonicalUrl(userId, canonicalUrl);
    }

    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public List<KnowledgeSource> findAllByIds(Collection<UUID> sourceIds) {
        return sourceRepository.findAllByIds(sourceIds);
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
}
