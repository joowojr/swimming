package com.swimming.backend.knowledge.domain;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeBranchTest {

    private static final Long USER_ID = 1L;

    @Test
    @DisplayName("노드 아래에 다른 노드를 배치한다")
    void createsBranchUnderNode() {
        KnowledgeNode folder = KnowledgeNode.create(USER_ID, NodeType.TOPIC, "MCP 서버 구현하기", null);
        KnowledgeNode source = KnowledgeNode.create(USER_ID, NodeType.SOURCE, "MCP Reference", null);

        KnowledgeBranch branch = KnowledgeBranch.create(folder, source, 0);

        assertThat(branch.getParentNodeId()).isEqualTo(folder.getId());
        assertThat(branch.getChildNodeId()).isEqualTo(source.getId());
        assertThat(branch.getPosition()).isZero();
    }

    @Test
    @DisplayName("자기 자신을 하위로 둘 수 없다")
    void rejectsSelfBranch() {
        KnowledgeNode folder = KnowledgeNode.create(USER_ID, NodeType.TOPIC, "MCP 서버 구현하기", null);

        assertThatThrownBy(() -> KnowledgeBranch.create(folder, folder, 0))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_KNOWLEDGE_BRANCH);
    }
}
