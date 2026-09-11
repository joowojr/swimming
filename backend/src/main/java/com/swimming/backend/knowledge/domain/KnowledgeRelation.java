package com.swimming.backend.knowledge.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Swimming이 해석한 지식의 의미 연결.
 * (fromNodeId, toNodeId, relationType)이 자연키다.
 */
@Getter
public class KnowledgeRelation {

    private final UUID fromNodeId;
    private final UUID toNodeId;
    private final RelationType relationType;

    private RelationOrigin origin;
    private Double confidence;
    private String evidence;

    private final Instant createdAt;

    private KnowledgeRelation(
            UUID fromNodeId,
            UUID toNodeId,
            RelationType relationType,
            RelationOrigin origin,
            Double confidence,
            String evidence,
            Instant createdAt
    ) {
        this.fromNodeId = fromNodeId;
        this.toNodeId = toNodeId;
        this.relationType = relationType;
        this.origin = origin;
        this.confidence = confidence;
        this.evidence = evidence;
        this.createdAt = createdAt;
    }

    public static KnowledgeRelation create(
            KnowledgeNode from,
            KnowledgeNode to,
            RelationType relationType,
            RelationOrigin origin,
            Double confidence,
            String evidence
    ) {
        relationType.validateEndpoints(from.getNodeType(), to.getNodeType());

        return new KnowledgeRelation(
                from.getId(),
                to.getId(),
                relationType,
                origin,
                confidence,
                evidence,
                null
        );
    }

    public static KnowledgeRelation restore(
            UUID fromNodeId,
            UUID toNodeId,
            RelationType relationType,
            RelationOrigin origin,
            Double confidence,
            String evidence,
            Instant createdAt
    ) {
        return new KnowledgeRelation(
                fromNodeId,
                toNodeId,
                relationType,
                origin,
                confidence,
                evidence,
                createdAt
        );
    }

    /**
     * 다시 관찰한 내용을 이 관계에 반영한다.
     *
     * <p>사용자가 만든 관계에 AI 관찰이 오면 아무것도 하지 않는다. 사람이 직접 이은 것을
     * 자동 관찰이 덮지 않게 한다.
     */
    public void applyObservation(
            RelationOrigin origin,
            Double confidence,
            String evidence
    ) {
        if (this.origin == RelationOrigin.USER && origin != RelationOrigin.USER) {
            return;
        }

        this.origin = origin;
        this.confidence = confidence;
        this.evidence = evidence;
    }
}
