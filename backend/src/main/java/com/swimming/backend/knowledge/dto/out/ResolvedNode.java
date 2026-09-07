package com.swimming.backend.knowledge.dto.out;

import com.swimming.backend.knowledge.domain.KnowledgeNode;

/**
 * 후보 이름 하나가 어느 노드로 정해졌는지.
 *
 * @param candidate LLM이 현재 Source에서 추출한 이름. 재사용된 노드 title과 다를 수 있다.
 * @param node      재사용하기로 한 기존 노드이거나 새로 만든 노드
 * @param match     어떤 단계에서 정해졌는지
 */
public record ResolvedNode(
        String candidate,
        KnowledgeNode node,
        Match match
) {

    /**
     * 판정 단계. 규칙 기반 일치, 유사 Source 문맥을 통한 의미 일치, 신규 생성으로 나뉜다.
     */
    public enum Match {

        /** 정규화한 이름이 기존 노드와 일치했다. */
        EXACT,

        /** 유사 Source의 Subject 중 같은 개념을 LLM이 골랐다. */
        SEMANTIC,

        /** 재사용할 노드가 없어 새로 만들었다. */
        CREATED
    }

    public static ResolvedNode exact(String candidate, KnowledgeNode node) {
        return new ResolvedNode(candidate, node, Match.EXACT);
    }

    public static ResolvedNode created(String candidate, KnowledgeNode node) {
        return new ResolvedNode(candidate, node, Match.CREATED);
    }

    public static ResolvedNode semantic(String candidate, KnowledgeNode node) {
        return new ResolvedNode(candidate, node, Match.SEMANTIC);
    }
}
