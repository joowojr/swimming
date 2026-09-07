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
     * 같은 관계를 다시 관찰했을 때 근거를 갱신한다.
     * 사용자가 만든 관계는 AI 재관찰로 덮어쓰지 않는다.
     */
    public void reinforce(
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
