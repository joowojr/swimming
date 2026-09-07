package com.swimming.backend.knowledge.usecase;

import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.dto.in.SourceConcepts;
import com.swimming.backend.knowledge.dto.in.SourceResponse;
import com.swimming.backend.knowledge.dto.in.SourceCollectRequest;
import com.swimming.backend.knowledge.dto.in.SourceCollectResponse;
import com.swimming.backend.knowledge.dto.out.FetchedDocument;
import com.swimming.backend.knowledge.dto.out.SourceFetchResult;
import com.swimming.backend.knowledge.service.KnowledgeSourceService;
import com.swimming.backend.knowledge.service.SourceConceptReader;
import com.swimming.backend.knowledge.service.SourceFetchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Folder에 링크를 저장한다. 본문을 가져오는 것부터 AI 소화까지 여기서 끝난다.
 *
 * <p>Folder는 지식 그래프의 노드가 아니라 Task와 같은 기존 도메인이다. 그래서 소속은
 * {@code knowledge_source.folder_id} 로 표현한다.
 *
 * <p>소화를 별도 호출로 나누지 않는다. 나누면 클라이언트가 저장 뒤 무엇을 더 불러야 하는지
 * 알아야 하고, 그 호출을 빠뜨린 Source가 조용히 소화되지 않은 채 남는다.
 *
 * <p>지금은 응답 전에 소화까지 마친다. 링크가 많으면 그만큼 오래 걸리므로, 나중에는 저장만
 * 하고 응답한 뒤 이어서 소화하게 바꾼다. 그때 바뀌는 것은 카드의 상태값뿐이고 이 메서드의
 * 계약은 그대로다.
 *
 * <p>네트워크를 타는 수집은 트랜잭션 밖에서 한 번에 처리하고, 저장은 Source마다 따로 커밋한다.
 * 한 링크가 실패해도 나머지는 남는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SourceCollectUseCase {

    private final SourceFetchService sourceFetchService;
    private final FolderService folderService;
    private final KnowledgeSourceService sourceService;
    private final SourceDigestUseCase digestUseCase;
    private final SourceConceptReader conceptReader;

    public SourceCollectResponse collect(
            Long userId,
            Long folderId,
            SourceCollectRequest request
    ) {
        folderService.validateOwnership(userId, folderId);

        List<SourceFetchResult> fetched = sourceFetchService.fetchAll(request.urls());

        // 저장·소화를 먼저 끝내고 카드는 마지막에 한 번에 만든다. 링크마다 만들면 관계 조회가
        // 링크 수만큼 나간다.
        List<Saved> saved = new ArrayList<>(fetched.size());
        for (SourceFetchResult result : fetched) {
            saved.add(save(userId, folderId, result));
        }

        // 이 Folder에 새로 만든 것이 있으면 링크가 있다는 뜻이다. 세어 볼 필요가 없다.
        // ALREADY_SAVED는 근거가 못 된다. 같은 문서가 다른 Folder에 저장돼 있을 수 있다.
        if (saved.stream().anyMatch(item -> item.result() == SourceCollectResponse.Result.CREATED)) {
            folderService.updateHasSource(userId, folderId, true);
        }

        return new SourceCollectResponse(toItems(userId, saved));
    }

    /**
     * @param sourceId 가져오기에 실패했으면 null
     */
    private record Saved(
            String url,
            SourceCollectResponse.Result result,
            UUID sourceId,
            SourceFetchResult.Failure failure
    ) {
    }

    private Saved save(Long userId, Long folderId, SourceFetchResult result) {
        if (!result.isSuccess()) {
            log.info(
                    "[source-collect] skipped url={} reason={} detail={}",
                    result.requestedUrl(), result.failure(), result.failureDetail()
            );
            return new Saved(
                    result.requestedUrl(), SourceCollectResponse.Result.FAILED, null, result.failure()
            );
        }

        FetchedDocument document = result.document();

        // 같은 문서를 이미 저장했으면 다시 만들지도, 다시 소화하지도 않는다.
        return sourceService.findByCanonicalUrl(userId, document.canonicalUrl())
                .map(existing -> new Saved(
                        result.requestedUrl(),
                        SourceCollectResponse.Result.ALREADY_SAVED,
                        existing.getId(),
                        null
                ))
                .orElseGet(() -> {
                    KnowledgeSource created =
                            sourceService.save(toSource(userId, folderId, document));

                    // 소화가 실패해도 예외를 던지지 않는다. 상태만 남고 원문은 그대로 있다.
                    digestUseCase.digest(userId, created.getId());

                    return new Saved(
                            result.requestedUrl(),
                            SourceCollectResponse.Result.CREATED,
                            created.getId(),
                            null
                    );
                });
    }

    private List<SourceCollectResponse.Item> toItems(Long userId, List<Saved> saved) {
        List<UUID> sourceIds = saved.stream()
                .map(Saved::sourceId)
                .filter(java.util.Objects::nonNull)
                .toList();

        List<KnowledgeSource> sources = sourceService.findAllByIds(sourceIds);
        Map<UUID, SourceConcepts> concepts = conceptReader.readAll(sources);

        Map<UUID, SourceResponse> responses = new LinkedHashMap<>();
        for (KnowledgeSource source : sources) {
            responses.put(source.getId(), SourceResponse.of(source, concepts.get(source.getId())));
        }

        return saved.stream()
                .map(item -> switch (item.result()) {
                    case FAILED -> SourceCollectResponse.Item.failed(item.url(), item.failure());
                    case CREATED -> SourceCollectResponse.Item.created(
                            item.url(), responses.get(item.sourceId())
                    );
                    case ALREADY_SAVED -> SourceCollectResponse.Item.alreadySaved(
                            item.url(), responses.get(item.sourceId())
                    );
                })
                .toList();
    }

    private KnowledgeSource toSource(Long userId, Long folderId, FetchedDocument document) {
        KnowledgeSource source = KnowledgeSource.create(
                userId,
                folderId,
                document.title(),
                document.url(),
                document.canonicalUrl()
        );

        source.applyExtractedDocument(
                document.title(),
                document.markdown(),
                document.sourceType(),
                document.author(),
                document.publishedAt()
        );

        return source;
    }
}
