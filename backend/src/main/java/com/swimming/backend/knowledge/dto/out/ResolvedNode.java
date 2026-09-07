package com.swimming.backend.knowledge.dto.out;

import com.swimming.backend.knowledge.domain.KnowledgeNode;

/**
 * 후보 이름 하나가 어느 노드로 정해졌는지.
 *
 * @param candidate LLM이 실제로 적어 낸 이름. 노드 title과 다를 수 있다. 어떤 표기가 어느
 *                  노드로 갔는지는 나중에 alias 사전을 만들 때의 입력이라 버리지 않는다.
 * @param node      재사용하기로 한 기존 노드이거나 새로 만든 노드
 * @param match     어떤 단계에서 정해졌는지
 */
public record ResolvedNode(
        String candidate,
        KnowledgeNode node,
        Match match
) {

    /**
     * 판정 단계. 지금은 정규화한 이름이 그대로 일치하거나(EXACT), 일치하는 것이 없어 새로
     * 만들거나(CREATED) 둘뿐이다. alias 사전이나 embedding 비교가 붙으면 값이 늘어난다.
     */
    public enum Match {

        /** 정규화한 이름이 기존 노드와 일치했다. */
        EXACT,

        /** 재사용할 노드가 없어 새로 만들었다. */
        CREATED
    }

    public static ResolvedNode exact(String candidate, KnowledgeNode node) {
        return new ResolvedNode(candidate, node, Match.EXACT);
    }

    public static ResolvedNode created(String candidate, KnowledgeNode node) {
        return new ResolvedNode(candidate, node, Match.CREATED);
    }
}
