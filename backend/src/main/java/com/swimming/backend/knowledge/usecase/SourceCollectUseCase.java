package com.swimming.backend.knowledge.usecase;

import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.dto.in.SourceCollectRequest;
import com.swimming.backend.knowledge.dto.in.SourceCollectResponse;
import com.swimming.backend.knowledge.dto.in.SourceConcepts;
import com.swimming.backend.knowledge.dto.in.SourceResponse;
import com.swimming.backend.knowledge.dto.out.FetchedDocument;
import com.swimming.backend.knowledge.dto.out.SourceFetchResult;
import com.swimming.backend.knowledge.service.SourceGraphReader;
import com.swimming.backend.knowledge.service.crawl.WebFetchService;
import com.swimming.backend.knowledge.service.data.KnowledgeSourceService;
import com.swimming.backend.knowledge.service.llm.SourceDigestProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 링크를 모으고 다시 소화한다. 삭제는 {@link SourceDeleteUseCase}가 맡는다.
 *
 * <p>Folder는 지식 그래프의 노드가 아니라 Task와 같은 기존 도메인이다. 그래서 소속은
 * {@code knowledge_source.folder_id} 로 표현한다. 새로 저장한 링크가 있으면 폴더의 링크 보유 표시를 켠다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SourceCollectUseCase {

    private final WebFetchService sourceFetchService;

    private final FolderService folderService;
    private final KnowledgeSourceService sourceService;

    private final SourceDigestProcessor digestProcessor;
    private final SourceGraphReader conceptReader;

    /**
     * Folder에 링크를 저장한다. 본문을 가져오는 것부터 AI 소화까지 여기서 끝난다.
     *
     * <p>소화를 별도 호출로 나누지 않는다. 나누면 클라이언트가 저장 뒤 무엇을 더 불러야
     * 하는지 알아야 하고, 그 호출을 빠뜨린 Source가 조용히 소화되지 않은 채 남는다.
     *
     * <p>지금은 응답 전에 소화까지 마친다. 링크가 많으면 그만큼 오래 걸리므로, 나중에는
     * 저장만 하고 응답한 뒤 이어서 소화하게 바꾼다. 그때 바뀌는 것은 카드의 상태값뿐이고
     * 이 메서드의 계약은 그대로다.
     *
     * <p>네트워크를 타는 수집은 트랜잭션 밖에서 한 번에 처리하고, 저장은 Source마다 따로
     * 커밋한다. 한 링크가 실패해도 나머지는 남는다.
     */
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
            saved.add(saveSourceGraph(userId, folderId, result));
        }

        // 새로 만들었거나 이미 있었거나, 어느 쪽이든 이 Folder에 링크가 있다는 뜻이다.
        // 중복 판정이 Folder 안만 보므로 ALREADY_SAVED도 이 Folder의 링크를 가리킨다.
        if (saved.stream().anyMatch(item -> item.result() != SourceCollectResponse.Result.FAILED)) {
            folderService.updateHasSource(userId, folderId, true);
        }

        return new SourceCollectResponse(toItems(userId, saved));
    }

    /** 이미 수집한 본문으로 LLM 소화만 다시 실행한다. */
    public SourceResponse retry(Long userId, UUID sourceId) {
        KnowledgeSource source = sourceService.getOwned(sourceId, userId);
        source.prepareRetry();
        sourceService.save(source);

        digestProcessor.digest(userId, sourceId);

        KnowledgeSource result = sourceService.getOwned(sourceId, userId);
        return SourceResponse.of(result, conceptReader.read(result));
    }

    /**
     * @param sourceId 가져오기에 실패했으면 null
     */
    private record Saved(
            String url,
            SourceCollectResponse.Result result,
            UUID sourceId,
            SourceFetchResult.Failure failure,
            boolean retryable
    ) {
    }

    private Saved saveSourceGraph(Long userId, Long folderId, SourceFetchResult result) {
        if (!result.isSuccess()) {
            log.info(
                    "[source-collect] skipped url={} reason={} detail={}",
                    result.requestedUrl(), result.failure(), result.failureDetail()
            );
            return new Saved(
                    result.requestedUrl(), SourceCollectResponse.Result.FAILED, null,
                    result.failure(), result.isRetryable()
            );
        }

        FetchedDocument document = result.document();

        // 이 Folder에 같은 문서가 이미 있으면 다시 만들지도, 다시 소화하지도 않는다.
        // 다른 Folder에 있는 같은 문서는 막지 않는다. 사용자는 폴더 단위로 링크를 모으므로
        // 저쪽 폴더에 있다는 이유로 이 폴더에서 저장이 거절되면 링크가 사라진 것처럼 보인다.
        return sourceService.findInFolderByCanonicalUrl(userId, folderId, document.canonicalUrl())
                .map(existing -> new Saved(
                        result.requestedUrl(),
                        SourceCollectResponse.Result.ALREADY_SAVED,
                        existing.getId(),
                        null,
                        false
                ))
                .orElseGet(() -> {
                    KnowledgeSource created =
                            sourceService.save(toSource(userId, folderId, document));

                    // 소화가 실패해도 예외를 던지지 않는다. 상태만 남고 원문은 그대로 있다.
                    digestProcessor.digest(userId, created.getId());

                    return new Saved(
                            result.requestedUrl(),
                            SourceCollectResponse.Result.CREATED,
                            created.getId(),
                            null,
                            false
                    );
                });
    }

    private List<SourceCollectResponse.Item> toItems(Long userId, List<Saved> saved) {
        List<UUID> sourceIds = saved.stream()
                .map(Saved::sourceId)
                .filter(Objects::nonNull)
                .toList();

        List<KnowledgeSource> sources = sourceService.findAllByIds(sourceIds);
        Map<UUID, SourceConcepts> concepts = conceptReader.readAll(sources);

        Map<UUID, SourceResponse> responses = new LinkedHashMap<>();
        for (KnowledgeSource source : sources) {
            responses.put(source.getId(), SourceResponse.of(source, concepts.get(source.getId())));
        }

        return saved.stream()
                .map(item -> switch (item.result()) {
                    case FAILED -> SourceCollectResponse.Item.failed(
                            item.url(), fetchFailureMessage(item.failure()), item.retryable()
                    );
                    case CREATED -> SourceCollectResponse.Item.created(
                            item.url(), responses.get(item.sourceId())
                    );
                    case ALREADY_SAVED -> SourceCollectResponse.Item.alreadySaved(
                            item.url(), responses.get(item.sourceId())
                    );
                })
                .toList();
    }

    private String fetchFailureMessage(SourceFetchResult.Failure failure) {
        return switch (failure) {
            case INVALID_URL -> "링크 주소를 확인해 주세요.";
            case BLOCKED_ADDRESS -> "가져올 수 없는 주소예요.";
            case UNSUPPORTED_CONTENT_TYPE -> "웹 문서가 아니라 가져오지 못했어요.";
            case HTTP_ERROR -> "문서를 여는 데 실패했어요.";
            case TIMEOUT -> "응답이 늦어 가져오지 못했어요.";
            case EMPTY_CONTENT -> "가져올 본문이 없었어요.";
            case UNKNOWN -> "링크를 가져오지 못했어요.";
        };
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
