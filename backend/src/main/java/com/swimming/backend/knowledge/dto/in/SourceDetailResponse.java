package com.swimming.backend.knowledge.dto.in;

import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.SourceProcessingStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Source Detail. 목록 항목({@link SourceResponse})에 원문 정보를 더한 것이다.
 *
 * <p>원문({@code content})은 담지 않는다. 화면의 행동은 `원문 열기`이고 그것은 {@code url}로
 * 바깥 문서를 여는 것이다. 서비스가 원문을 다시 보여 주는 화면은 v0.4에 없다.
 *
 * @param publishedAt 문서가 바깥에서 발행된 시각. 원문에서 못 읽으면 비어 있다
 * @param createdAt   이 Source를 저장한 시각. {@code publishedAt}과 다른 축이다
 * @param readAt      이 링크를 읽은 시각. 아직 읽지 않았으면 {@code null}
 * @param status      {@code COMPLETED}가 아니면 아래 세 값은 비어 있다
 * @param topic       {@code COMPLETED}면 반드시 하나 있다
 */
public record SourceDetailResponse(
        UUID sourceId,
        String title,
        String url,
        String canonicalUrl,
        String domain,
        String sourceType,
        String author,
        Instant publishedAt,
        Long folderId,
        Instant createdAt,
        Instant readAt,
        SourceProcessingStatus status,
        String summary,
        NodeRef topic,
        List<NodeRef> subjects
) {

    public static SourceDetailResponse of(KnowledgeSource source, SourceConcepts concepts) {
        return new SourceDetailResponse(
                source.getId(),
                source.getNode().getTitle(),
                source.getUrl(),
                source.getCanonicalUrl(),
                SourceResponse.domainOf(source.getUrl()),
                source.getSourceType(),
                source.getAuthor(),
                source.getPublishedAt(),
                source.getFolderId(),
                source.getNode().getCreatedAt(),
                source.getReadAt(),
                source.getProcessingStatus(),
                source.getSummary(),
                concepts.topic(),
                concepts.subjects()
        );
    }
}
