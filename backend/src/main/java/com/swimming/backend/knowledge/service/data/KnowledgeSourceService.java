package com.swimming.backend.knowledge.service.data;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.folder.service.FolderService;
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
    private final FolderService folderService;

    /** 폴더 잠금 아래 중복을 확인하고 신규 링크만 저장한다. */
    @Transactional(propagation = Propagation.REQUIRED)
    public KnowledgeSource create(KnowledgeSource source) {
        Long userId = source.getUserId();
        Long folderId = source.getFolderId();
        folderService.lockOwned(userId, folderId);
        var existing = sourceRepository.findAllInFolderByCanonicalUrls(
                userId, folderId, List.of(source.getCanonicalUrl()));
        if (!existing.isEmpty()) return existing.getFirst();
        return sourceRepository.save(source);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public KnowledgeSource save(KnowledgeSource source) {
        folderService.lockOwned(source.getUserId(), source.getFolderId());
        getOwned(source.getId(), source.getUserId());
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
        folderService.lockOwned(source.getUserId(), source.getFolderId());
        KnowledgeSource current = getOwned(source.getId(), source.getUserId());
        current.delete();
        sourceRepository.save(current);

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

    /**
     * 다른 도메인이 문서를 가리킬 때 쓴다. 지운 문서와 남의 문서는 빠진다.
     *
     * <p>영속 Entity가 아니라 순수 도메인 객체를 돌려준다. 돌려준 개수가 요청한 개수보다
     * 적으면 그중에 없는 것이 섞여 있다는 뜻이다.
     */
    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public List<KnowledgeSource> getOwnedAll(Long userId, Collection<UUID> sourceIds) {
        return sourceRepository.findAllActiveByIds(userId, sourceIds);
    }

    /**
     * 위와 같되 그 폴더 안의 것만 돌려준다. 다른 폴더의 문서는 아예 돌아오지 않으므로
     * 부르는 쪽이 폴더를 다시 비교하지 않는다.
     */
    /**
     * Category 분류에 넣을 수 있는 Source를 이 Folder에서 읽는다.
     *
     * <p>소화가 끝나지 않았거나 실패한 Source, 다른 폴더·다른 사용자의 Source는 아예
     * 돌아오지 않는다. 부르는 쪽이 다시 거르지 않는다.
     */
    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public List<KnowledgeSource> getAllCategorizationTargets(
            Long userId,
            Long folderId,
            Collection<UUID> sourceIds
    ) {
        return sourceRepository.findAllCategorizationTargets(userId, folderId, sourceIds);
    }

    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public List<KnowledgeSource> getOwnedAllInFolder(
            Long userId,
            Long folderId,
            Collection<UUID> sourceIds
    ) {
        return sourceRepository.findAllActiveInFolderByIds(userId, folderId, sourceIds);
    }

    /**
     * 이 Folder에 살아 있는 Source의 node id.
     *
     * <p>Category를 soft delete하기 전에, 그 Source로 들어오는 {@code CONTAINS}의
     * from(Category)를 찾을 때 쓴다.
     */
    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public List<UUID> findAliveNodeIdsInFolder(Long userId, Long folderId) {
        return sourceRepository.findAliveNodeIdsInFolder(userId, folderId);
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
