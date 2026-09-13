package com.swimming.backend.knowledge.repository.postgres.entity;

import com.swimming.backend.common.entity.BaseTimeEntity;
import com.swimming.backend.knowledge.domain.SourceProcessingStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@DynamicUpdate
@Table(
        name = "knowledge_source",
        indexes = {
                @Index(
                        name = "idx_knowledge_source_canonical_url",
                        columnList = "canonical_url"
                ),
                @Index(
                        name = "idx_knowledge_source_folder",
                        columnList = "folder_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KnowledgeSourceEntity extends BaseTimeEntity {

    @Id
    @Column(name = "node_id")
    private UUID nodeId;

    @Column(name = "folder_id", nullable = false)
    private Long folderId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String url;

    @Column(name = "canonical_url", nullable = false, columnDefinition = "text")
    private String canonicalUrl;

    @Column(columnDefinition = "text")
    private String content;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(name = "source_type", length = 50)
    private String sourceType;

    @Column(length = 255)
    private String author;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 20)
    private SourceProcessingStatus processingStatus;

    @Column(name = "analysis_version")
    private Integer analysisVersion;

    @Column(name = "failure_message", length = 255)
    private String failureMessage;

    @Column(nullable = false)
    private boolean retryable;

    @Column(name = "read_at")
    private Instant readAt;

    @Builder
    private KnowledgeSourceEntity(
            UUID nodeId,
            Long folderId,
            String title,
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
        this.nodeId = nodeId;
        this.folderId = folderId;
        this.title = title;
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

    public void updateReadAt(Instant readAt) {
        this.readAt = readAt;
    }

    public void updateStatus(
            SourceProcessingStatus processingStatus,
            String failureMessage,
            boolean retryable
    ) {
        this.processingStatus = processingStatus;
        this.failureMessage = failureMessage;
        this.retryable = retryable;
    }
}
