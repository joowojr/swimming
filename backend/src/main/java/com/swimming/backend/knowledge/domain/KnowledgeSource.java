package com.swimming.backend.knowledge.domain;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
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
    private String failureMessage;
    private boolean retryable;

    /** 읽은 시각. 아직 읽지 않았으면 비어 있다. */
    private Instant readAt;

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
            Integer analysisVersion,
            String failureMessage,
            boolean retryable,
            Instant readAt
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
        this.failureMessage = failureMessage;
        this.retryable = retryable;
        this.readAt = readAt;
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
                null,
                null,
                false,
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
            Integer analysisVersion,
            String failureMessage,
            boolean retryable,
            Instant readAt
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
                analysisVersion,
                failureMessage,
                retryable,
                readAt
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
        this.failureMessage = null;
        this.retryable = false;
    }

    public void completeDigestion(String summary, Integer analysisVersion) {
        this.summary = summary;
        this.analysisVersion = analysisVersion;
        this.processingStatus = SourceProcessingStatus.COMPLETED;
        this.failureMessage = null;
        this.retryable = false;
    }

    /** 본문이 짧아 LLM 소화 없이 원문을 그대로 보여 주는 상태로 완료한다. */
    public void completeWithoutDigestion() {
        this.summary = null;
        this.analysisVersion = null;
        this.processingStatus = SourceProcessingStatus.SOURCE_NOT_DIGEST;
        this.failureMessage = null;
        this.retryable = false;
    }

    /**
     * AI 처리 실패는 원문 Source를 남긴 채 상태만 바꾼다.
     */
    public void failDigestion(String failureMessage, boolean retryable) {
        this.processingStatus = SourceProcessingStatus.FAILED;
        this.failureMessage = failureMessage;
        this.retryable = retryable;
    }

    /**
     * 읽음으로 표시한다. 시각은 서버가 정한다. 클라이언트 시계를 믿으면 읽은 링크를
     * 세는 집계가 흔들린다.
     *
     * <p>이미 읽은 링크를 다시 표시해도 처음 읽은 시각을 유지한다. 같은 요청이 겹쳐 와도
     * 결과가 같아야 하고, "언제 읽었나"는 마지막이 아니라 처음이 답이다.
     */
    public void markRead(Instant readAt) {
        if (this.readAt == null) {
            this.readAt = readAt;
        }
    }

    /** 읽음 표시를 되돌린다. 읽은 시각도 함께 지운다. */
    public void markUnread() {
        this.readAt = null;
    }

    public void updateStatus(SourceProcessingStatus status) {
        this.processingStatus = status;
    }

    /** 저장된 본문으로 다시 소화할 수 있도록 대기 상태로 돌린다. URL 수집은 반복하지 않는다. */
    public void prepareRetry() {
        boolean retryableStatus = processingStatus == SourceProcessingStatus.PENDING
                || (processingStatus == SourceProcessingStatus.FAILED && retryable);

        if (!retryableStatus || content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_SOURCE_NOT_RETRYABLE);
        }

        this.processingStatus = SourceProcessingStatus.PENDING;
        this.failureMessage = null;
        this.retryable = false;
    }
}
