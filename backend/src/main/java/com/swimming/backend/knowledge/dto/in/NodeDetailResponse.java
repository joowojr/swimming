package com.swimming.backend.knowledge.dto.in;

import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.NodeType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Subject와 Topic Detail. 노드 하나와, 그 노드에 걸린 문서·개념이다.
 *
 * <p>둘이 한 모양을 쓰는 이유는 화면이 같기 때문이다. 어느 쪽이 채워지는지만 다르다.
 *
 * <table>
 *   <tr><th>type</th><th>sources</th><th>topics</th><th>subjects</th></tr>
 *   <tr><td>SUBJECT</td><td>이 개념을 다루는 문서(N개)</td><td>이 개념이 걸리는 목적</td><td>비어 있음</td></tr>
 *   <tr><td>TOPIC</td><td>이 목적을 설명하는 문서(항상 1개)</td><td>비어 있음</td><td>이 목적이 걸치는 개념</td></tr>
 * </table>
 *
 * <p>Topic의 {@code sources}가 항상 1개인 것은 Topic을 Source마다 새로 만들고 재사용
 * 판정을 하지 않기 때문이다. 배열인 것은 Subject와 모양을 맞추기 위해서지 여러 개가 들어올
 * 자리라는 뜻이 아니다.
 *
 * @param createdAt 이 개념·목적이 그래프에 처음 생긴 시각. 언제부터 쌓아 온 것인지 보여 준다
 */
public record NodeDetailResponse(
        UUID nodeId,
        NodeType type,
        String title,
        Instant createdAt,
        List<SourceRef> sources,
        List<NodeRef> topics,
        List<NodeRef> subjects
) {

    /** 문서 목록에는 카드 전부가 필요 없다. 눌러서 Source Detail로 갈 수 있을 만큼만 준다. */
    public record SourceRef(UUID sourceId, String title, String summary) {

        public static SourceRef from(KnowledgeSource source) {
            return new SourceRef(
                    source.getId(),
                    source.getNode().getTitle(),
                    source.getSummary()
            );
        }
    }

    public static NodeDetailResponse of(
            KnowledgeNode node,
            List<SourceRef> sources,
            List<NodeRef> topics,
            List<NodeRef> subjects
    ) {
        return new NodeDetailResponse(
                node.getId(),
                node.getNodeType(),
                node.getTitle(),
                node.getCreatedAt(),
                sources,
                topics,
                subjects
        );
    }
}
