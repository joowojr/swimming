package com.swimming.backend.knowledge.dto.in;

import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.SourceProcessingStatus;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Source 한 건을 목록에 그리는 데 필요한 것만 담는다.
 *
 * <p>저장 응답과 목록 응답이 이 하나를 공유한다. 저장 직후와 목록이 같은 것을 그리므로
 * 응답이 갈라질 이유가 없다. 원문 정보까지 필요한 화면은 {@link SourceDetailResponse}다.
 *
 * <p>원문({@code content})은 담지 않는다. 문서 하나가 수만 자라 목록이 감당하지 못한다.
 *
 * @param createdAt 이 Source를 저장한 시각
 * @param status    소화가 어디까지 갔는지. {@code COMPLETED}가 아니면 아래 세 값은 비어 있다
 * @param topic     {@code COMPLETED}면 반드시 하나 있다
 * @param subjects  최대 4개
 */
public record SourceResponse(
        UUID sourceId,
        String title,
        String url,
        String domain,
        String sourceType,
        Instant createdAt,
        SourceProcessingStatus status,
        String summary,
        NodeRef topic,
        List<NodeRef> subjects
) {

    public static SourceResponse of(KnowledgeSource source, SourceConcepts concepts) {
        return new SourceResponse(
                source.getId(),
                source.getNode().getTitle(),
                source.getUrl(),
                domainOf(source.getUrl()),
                source.getSourceType(),
                source.getNode().getCreatedAt(),
                source.getProcessingStatus(),
                source.getSummary(),
                concepts.topic(),
                concepts.subjects()
        );
    }

    /**
     * 출처를 한눈에 알아보게 호스트만 뽑는다. 저장하지 않고 URL에서 그때그때 만든다.
     * 컬럼으로 두면 URL과 어긋날 수 있고, 어긋났을 때 고칠 방법이 없다.
     *
     * <p>Source Detail도 같은 값을 주므로 카드와 상세가 이 하나를 나눠 쓴다.
     */
    static String domainOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
