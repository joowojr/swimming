package com.swimming.backend.knowledge.domain;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public enum RelationType {

    ABOUT(NodeType.SOURCE, NodeType.SUBJECT),
    SUPPORTS(NodeType.SOURCE, NodeType.TOPIC),
    INVOLVES(NodeType.TOPIC, NodeType.SUBJECT),

    /**
     * Category가 Source를 담는다.
     *
     * <p>방향이 Category에서 Source로 가는 이유는, 이 관계를 읽는 쪽이 언제나
     * "이 묶음에 무엇이 들었나"이기 때문이다. Source 쪽에 컬럼으로 두지 않는 것은
     * 묶음이 Preview·Confirm으로 통째로 갈아엎히는 값이라 Source를 건드리지 않고
     * 관계만 지우고 다시 만드는 편이 단순하기 때문이다.
     */
    CONTAINS(NodeType.CATEGORY, NodeType.SOURCE);

    private final NodeType fromType;
    private final NodeType toType;

    RelationType(NodeType fromType, NodeType toType) {
        this.fromType = fromType;
        this.toType = toType;
    }

    private record Endpoints(NodeType from, NodeType to) {
    }

    private static final Map<Endpoints, RelationType> BY_ENDPOINTS = new HashMap<>();

    static {
        for (RelationType relationType : values()) {
            RelationType previous = BY_ENDPOINTS.put(
                    new Endpoints(relationType.fromType, relationType.toType),
                    relationType
            );

            // 양끝 노드 타입으로 관계를 찾으려면 그 조합이 관계 하나만 가리켜야 한다.
            // 같은 조합을 쓰는 관계를 추가하면 여기서 클래스 로딩이 실패한다. 그때는
            // 노드 타입만으로 고를 수 없으므로 호출부가 관계를 직접 지정해야 한다.
            if (previous != null) {
                throw new IllegalStateException(
                        "%s와 %s가 같은 양끝을 쓴다".formatted(previous, relationType)
                );
            }
        }
    }

    /**
     * 양끝 노드 타입으로 관계를 고른다.
     *
     * <p>v0.4의 세 관계는 각각 양끝 조합이 다르다. Source에서 Subject로 잇는 관계는
     * {@code ABOUT} 하나뿐이므로, 호출하는 쪽이 관계 이름을 따로 넘길 이유가 없다.
     */
    public static RelationType between(NodeType fromType, NodeType toType) {
        RelationType relationType = BY_ENDPOINTS.get(new Endpoints(fromType, toType));

        if (relationType == null) {
            throw new BusinessException(ErrorCode.INVALID_KNOWLEDGE_RELATION);
        }

        return relationType;
    }

    public void validateEndpoints(NodeType fromType, NodeType toType) {
        if (this.fromType != fromType || this.toType != toType) {
            throw new BusinessException(ErrorCode.INVALID_KNOWLEDGE_RELATION);
        }
    }
}
