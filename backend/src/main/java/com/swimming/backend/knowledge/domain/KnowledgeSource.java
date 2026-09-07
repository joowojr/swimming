package com.swimming.backend.knowledge.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * SOURCE 노드와 문서 전용 데이터를 함께 다루는 애그리거트.
 * 저장소는 이 단위로 저장한다.
 *
 * <p>Source는 Task와 마찬가지로 기존 Folder에 속한다. Folder는 지식 그래프의 노드가
 * 아니라 사용자가 일을 모아 두는 기존 도메인이므로, 그래프 관계가 아니라 외래키로 잇는다.
 */
@Getter
public class KnowledgeSource {

    private final KnowledgeNode node;

    private final Long folderId;

    private final String url;
    private final String canonicalUrl;

    private String content;
    private String summary;
    private String sourceType;
    private String author;
    private Instant publishedAt;

    private SourceProcessingStatus processingStatus;
    private Integer analysisVersion;

    private KnowledgeSource(
            KnowledgeNode node,
            Long folderId,
            String url,
            String canonicalUrl,
            String content,
            String summary,
            String sourceType,
            String author,
            Instant publishedAt,
            SourceProcessingStatus processingStatus,
            Integer analysisVersion
    ) {
        this.node = node;
        this.folderId = folderId;
        this.url = url;
        this.canonicalUrl = canonicalUrl;
        this.content = content;
        this.summary = summary;
        this.sourceType = sourceType;
        this.author = author;
        this.publishedAt = publishedAt;
        this.processingStatus = processingStatus;
        this.analysisVersion = analysisVersion;
    }

    public static KnowledgeSource create(
            Long userId,
            Long folderId,
            String title,
            String url,
            String canonicalUrl
    ) {
        return new KnowledgeSource(
                KnowledgeNode.create(userId, NodeType.SOURCE, title, null),
                folderId,
                url,
                canonicalUrl,
                null,
                null,
                null,
                null,
                null,
                SourceProcessingStatus.PENDING,
                null
        );
    }

    public static KnowledgeSource restore(
            KnowledgeNode node,
            Long folderId,
            String url,
            String canonicalUrl,
            String content,
            String summary,
            String sourceType,
            String author,
            Instant publishedAt,
            SourceProcessingStatus processingStatus,
            Integer analysisVersion
    ) {
        return new KnowledgeSource(
                node,
                folderId,
                url,
                canonicalUrl,
                content,
                summary,
                sourceType,
                author,
                publishedAt,
                processingStatus,
                analysisVersion
        );
    }

    public UUID getId() {
        return node.getId();
    }

    public Long getUserId() {
        return node.getUserId();
    }

    public boolean isDeleted() {
        return node.isDeleted();
    }

    /**
     * 문서를 지운다. 원문과 관계는 그대로 두고 노드만 지운 것으로 표시한다.
     *
     * <p>딸린 Topic은 여기서 다루지 않는다. Topic 노드를 아는 것은 관계이지 이 애그리거트가
     * 아니므로 UseCase가 함께 지운다.
     */
    public void delete() {
        node.delete();
    }

    public void applyExtractedDocument(
            String title,
            String content,
            String sourceType,
            String author,
            Instant publishedAt
    ) {
        this.node.rename(title);
        this.content = content;
        this.sourceType = sourceType;
        this.author = author;
        this.publishedAt = publishedAt;
    }

    public void startDigestion() {
        this.processingStatus = SourceProcessingStatus.PROCESSING;
    }

    public void completeDigestion(String summary, Integer analysisVersion) {
        this.summary = summary;
        this.analysisVersion = analysisVersion;
        this.processingStatus = SourceProcessingStatus.COMPLETED;
    }

    /**
     * AI 처리 실패는 원문 Source를 남긴 채 상태만 바꾼다.
     */
    public void failDigestion() {
        this.processingStatus = SourceProcessingStatus.FAILED;
    }
}
