package com.swimming.backend.knowledge.service;

import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.dto.out.ResolvedNode;
import com.swimming.backend.knowledge.repository.InMemoryKnowledgeRepositories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NodeResolverTest {

    private static final Long USER_ID = 1L;

    private InMemoryKnowledgeRepositories.Nodes nodes;
    private NodeResolver resolver;

    @BeforeEach
    void setUp() {
        nodes = new InMemoryKnowledgeRepositories.Nodes();
        resolver = new NodeResolver(nodes);
    }

    private KnowledgeNode subject(String title) {
        return resolver.resolveSubjects(USER_ID, List.of(title)).getFirst().node();
    }

    @Test
    @DisplayName("이미 저장된 개념을 표기만 다르게 적어 오면 새 노드를 만들지 않는다")
    void 저장된_노드를_재사용한다() {
        KnowledgeNode existing = subject("OIDC");

        List<ResolvedNode> resolved =
                resolver.resolveSubjects(USER_ID, List.of("oidc", "AWS-OIDC"));

        assertThat(resolved).extracting(ResolvedNode::match)
                .containsExactly(ResolvedNode.Match.EXACT, ResolvedNode.Match.CREATED);
        assertThat(resolved.getFirst().node().getId()).isEqualTo(existing.getId());
    }

    @Test
    @DisplayName("재사용한 노드의 제목은 처음 저장한 표기를 그대로 둔다")
    void 제목을_덮어쓰지_않는다() {
        subject("OIDC");

        ResolvedNode resolved = resolver.resolveSubjects(USER_ID, List.of("aws oidc", "oidc")).get(1);

        assertThat(resolved.candidate()).isEqualTo("oidc");
        assertThat(resolved.node().getTitle()).isEqualTo("OIDC");
    }

    @Test
    @DisplayName("한 응답 안에서 같은 개념이 여러 번 나와도 노드는 하나만 만든다")
    void 요청_안의_중복도_걷어낸다() {
        List<ResolvedNode> resolved =
                resolver.resolveSubjects(USER_ID, List.of("OIDC", "oidc", "O I D C"));

        assertThat(resolved).hasSize(1);
        assertThat(resolved.getFirst().node().getTitle()).isEqualTo("OIDC");
        assertThat(nodes.findAllByUserIdAndNodeType(USER_ID, NodeType.SUBJECT)).hasSize(1);
    }

    @Test
    @DisplayName("사용자가 다르면 같은 이름이어도 각자의 노드를 갖는다")
    void 사용자별로_노드를_나눈다() {
        KnowledgeNode mine = subject("OIDC");
        KnowledgeNode other = resolver.resolveSubjects(2L, List.of("oidc")).getFirst().node();

        assertThat(other.getId()).isNotEqualTo(mine.getId());
    }

    @Test
    @DisplayName("이름이 비어 있으면 노드를 만들지 않고 건너뛴다")
    void 비어_있는_이름은_건너뛴다() {
        List<ResolvedNode> resolved =
                resolver.resolveSubjects(USER_ID, List.of("   ", "-_-", "OIDC"));

        assertThat(resolved).hasSize(1);
        assertThat(resolved.getFirst().node().getTitle()).isEqualTo("OIDC");
    }

    @Test
    @DisplayName("Subject만 대상이다. 이름이 같은 Topic이 있어도 재사용하지 않는다")
    void 서브젝트만_재사용한다() {
        KnowledgeNode topic = nodes.save(KnowledgeNode.create(USER_ID, NodeType.TOPIC, "OIDC", null));

        ResolvedNode resolved = resolver.resolveSubjects(USER_ID, List.of("OIDC")).getFirst();

        assertThat(resolved.match()).isEqualTo(ResolvedNode.Match.CREATED);
        assertThat(resolved.node().getId()).isNotEqualTo(topic.getId());
        assertThat(resolved.node().getNodeType()).isEqualTo(NodeType.SUBJECT);
    }
}
