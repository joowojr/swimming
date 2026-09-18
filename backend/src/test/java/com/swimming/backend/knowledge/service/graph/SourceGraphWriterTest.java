package com.swimming.backend.knowledge.service.graph;

import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.knowledge.domain.KnowledgeRelation;
import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.domain.RelationOrigin;
import com.swimming.backend.knowledge.domain.RelationType;
import com.swimming.backend.knowledge.dto.out.CategoryAssignmentDecision;
import com.swimming.backend.knowledge.dto.out.SourceDigestResult;
import com.swimming.backend.knowledge.dto.out.ResolvedNode;
import com.swimming.backend.knowledge.repository.InMemoryKnowledgeRepositories;
import com.swimming.backend.knowledge.service.data.KnowledgeNodeService;
import com.swimming.backend.knowledge.service.data.KnowledgeRelationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SourceGraphWriterTest {

    private static final Long USER_ID = 1L;
    private static final Long FOLDER_ID = 10L;

    private InMemoryKnowledgeRepositories.Nodes nodes;
    private InMemoryKnowledgeRepositories.Relations relations;
    private InMemoryKnowledgeRepositories.Sources sources;
    private FolderService folderService;
    private SourceGraphWriter writer;

    @BeforeEach
    void setUp() {
        relations = new InMemoryKnowledgeRepositories.Relations();
        sources = new InMemoryKnowledgeRepositories.Sources();
        nodes = new InMemoryKnowledgeRepositories.Nodes().withFolderGraph(sources, relations);
        folderService = mock(FolderService.class);

        writer = new SourceGraphWriter(
                new KnowledgeNodeService(nodes),
                new KnowledgeRelationService(relations),
                folderService
        );
    }

    private KnowledgeSource source(String url) {
        return KnowledgeSource.create(USER_ID, FOLDER_ID, "Spring AI MCP Reference", url, url);
    }

    private SourceDigestResult result(String topic, String... subjects) {
        return new SourceDigestResult("요약", "백엔드", topic, List.of(subjects));
    }

    private void write(KnowledgeSource source, SourceDigestResult result) {
        List<ResolvedNode> subjects = result.subjects().stream()
                .map(title -> ResolvedNode.created(
                        title,
                        nodes.create(KnowledgeNode.create(USER_ID, NodeType.SUBJECT, title, null))
                ))
                .toList();
        writer.write(source, result, subjects);
    }

    private List<KnowledgeRelation> from(UUID fromNodeId, RelationType relationType) {
        return relations.findAllByFromNodeIdIn(List.of(fromNodeId), List.of(relationType));
    }

    private String titleOf(UUID nodeId) {
        return nodes.findById(nodeId).orElseThrow().getTitle();
    }

    @Test
    @DisplayName("문서가 다룬 개념은 ABOUT으로, 적용 목적은 SUPPORTS로 잇는다")
    void 소스를_개념과_목적에_잇는다() {
        KnowledgeSource source = source("https://a.com/mcp");

        write(source, result("MCP 서버 구현하기", "MCP", "Tool Calling"));

        UUID sourceNodeId = source.getNode().getId();
        assertThat(from(sourceNodeId, RelationType.ABOUT))
                .extracting(relation -> titleOf(relation.getToNodeId()))
                .containsExactlyInAnyOrder("MCP", "Tool Calling");

        List<KnowledgeRelation> supports = from(sourceNodeId, RelationType.SUPPORTS);
        assertThat(supports).hasSize(1);
        assertThat(titleOf(supports.getFirst().getToNodeId())).isEqualTo("MCP 서버 구현하기");
    }

    @Test
    @DisplayName("INVOLVES는 LLM 출력 없이 Topic과 그 Source의 Subject로 파생한다")
    void involves를_파생한다() {
        KnowledgeSource source = source("https://a.com/mcp");

        write(source, result("MCP 서버 구현하기", "MCP", "Tool Calling"));

        UUID topicId = from(source.getNode().getId(), RelationType.SUPPORTS)
                .getFirst().getToNodeId();

        assertThat(from(topicId, RelationType.INVOLVES))
                .extracting(relation -> titleOf(relation.getToNodeId()))
                .containsExactlyInAnyOrder("MCP", "Tool Calling");
    }

    @Test
    @DisplayName("Topic은 재사용하지 않는다. 이름이 같아도 Source마다 새로 만든다")
    void 목적은_매번_만든다() {
        write(source("https://a.com/1"), result("MCP 서버 구현하기", "MCP"));
        write(source("https://a.com/2"), result("MCP 서버 구현하기", "MCP"));

        assertThat(nodes.findAllByUserIdAndNodeType(USER_ID, NodeType.TOPIC)).hasSize(2);
    }

    @Test
    @DisplayName("같은 문서를 다시 반영해도 관계가 늘지 않는다")
    void 관계를_중복_저장하지_않는다() {
        KnowledgeSource source = source("https://a.com/mcp");

        KnowledgeNode subject = nodes.create(KnowledgeNode.create(
                USER_ID, NodeType.SUBJECT, "MCP", null
        ));
        List<ResolvedNode> resolved = List.of(ResolvedNode.created("MCP", subject));

        writer.write(source, result("MCP 서버 구현하기", "MCP"), resolved);
        writer.write(source, result("MCP 서버 구현하기", "MCP"), resolved);

        assertThat(from(source.getNode().getId(), RelationType.ABOUT)).hasSize(1);
    }

    /** 이 폴더의 Source 하나를 담은 Category. 폴더 소속은 담긴 Source에서 파생한다. */
    private KnowledgeNode categoryInFolder(String title) {
        KnowledgeSource member = sources.save(source("https://a.com/member/" + UUID.randomUUID()));
        KnowledgeNode category = nodes.create(KnowledgeNode.create(USER_ID, NodeType.CATEGORY, title, null));
        new KnowledgeRelationService(relations).connect(category, member.getNode(), RelationOrigin.USER);
        return category;
    }

    private List<UUID> categoriesContaining(KnowledgeSource source) {
        return relations.findAllByToNodeIdIn(List.of(source.getNode().getId()), List.of(RelationType.CONTAINS))
                .stream().map(KnowledgeRelation::getFromNodeId).toList();
    }

    @Test
    @DisplayName("고른 Category가 이 폴더의 살아 있는 Category면 그곳에 담는다")
    void 기존_카테고리에_담는다() {
        KnowledgeNode category = categoryInFolder("MCP 서버 구현");
        KnowledgeSource target = sources.save(source("https://a.com/new"));

        boolean written = writer.writeCategory(target, new CategoryAssignmentDecision.Reuse(category.getId()));

        assertThat(written).isTrue();
        assertThat(categoriesContaining(target)).containsExactly(category.getId());
        verify(folderService).lockOwned(USER_ID, FOLDER_ID);
    }

    @Test
    @DisplayName("고른 Category가 이 폴더의 살아 있는 Category가 아니면 그 배정을 쓰지 않는다")
    void 폴더_밖_카테고리는_쓰지_않는다() {
        categoryInFolder("MCP 서버 구현");
        KnowledgeNode deleted = categoryInFolder("WAL 정리");
        nodes.softDeleteAllOwnedByIds(USER_ID, List.of(deleted.getId()));
        KnowledgeSource target = sources.save(source("https://a.com/new"));

        boolean unknown = writer.writeCategory(target, new CategoryAssignmentDecision.Reuse(UUID.randomUUID()));
        boolean dead = writer.writeCategory(target, new CategoryAssignmentDecision.Reuse(deleted.getId()));

        assertThat(unknown).isFalse();
        assertThat(dead).isFalse();
        assertThat(categoriesContaining(target)).isEmpty();
    }

    @Test
    @DisplayName("새 이름이 기존 이름과 정규화 기준으로 같으면 새로 만들지 않고 기존 Category에 담는다")
    void 정규화_기준_같은_이름은_기존에_담는다() {
        KnowledgeNode category = categoryInFolder("MCP 서버 구현");
        KnowledgeSource target = sources.save(source("https://a.com/new"));

        boolean written = writer.writeCategory(target, new CategoryAssignmentDecision.Create("mcp서버 구현"));

        assertThat(written).isTrue();
        assertThat(categoriesContaining(target)).containsExactly(category.getId());
        assertThat(nodes.findAllByUserIdAndNodeType(USER_ID, NodeType.CATEGORY)).hasSize(1);
    }

    @Test
    @DisplayName("맞는 기존 Category가 없으면 새 Category를 만들어 그 Source 하나를 담는다")
    void 새_카테고리를_만든다() {
        categoryInFolder("MCP 서버 구현");
        KnowledgeSource target = sources.save(source("https://a.com/new"));

        boolean written = writer.writeCategory(target, new CategoryAssignmentDecision.Create("WAL 정리"));

        assertThat(written).isTrue();
        List<UUID> contained = categoriesContaining(target);
        assertThat(contained).hasSize(1);
        assertThat(titleOf(contained.getFirst())).isEqualTo("WAL 정리");
        assertThat(nodes.findById(contained.getFirst()).orElseThrow().getNodeType()).isEqualTo(NodeType.CATEGORY);
    }

    @Test
    @DisplayName("판정을 기다리는 동안 폴더의 Category 구성이 사라졌으면 새로 만들지 않는다")
    void 구성이_없으면_배정하지_않는다() {
        KnowledgeSource target = sources.save(source("https://a.com/new"));

        boolean written = writer.writeCategory(target, new CategoryAssignmentDecision.Create("WAL 정리"));

        assertThat(written).isFalse();
        assertThat(nodes.findAllByUserIdAndNodeType(USER_ID, NodeType.CATEGORY)).isEmpty();
    }

    @Test
    @DisplayName("판정을 기다리는 동안 확정으로 이미 담긴 Source면 사용자의 배치를 따른다")
    void 이미_담긴_소스는_옮기지_않는다() {
        KnowledgeNode confirmed = categoryInFolder("MCP 서버 구현");
        KnowledgeNode other = categoryInFolder("WAL 정리");
        KnowledgeSource target = sources.save(source("https://a.com/new"));
        new KnowledgeRelationService(relations).connect(confirmed, target.getNode(), RelationOrigin.USER);

        boolean written = writer.writeCategory(target, new CategoryAssignmentDecision.Reuse(other.getId()));

        assertThat(written).isFalse();
        assertThat(categoriesContaining(target)).containsExactly(confirmed.getId());
    }

    @Test
    @DisplayName("맞는 기존 Category가 없다는 판정이면 아무것도 담지 않는다")
    void 건너뛰기_판정은_담지_않는다() {
        categoryInFolder("MCP 서버 구현");
        KnowledgeSource target = sources.save(source("https://a.com/new"));

        boolean written = writer.writeCategory(target, new CategoryAssignmentDecision.Skip());

        assertThat(written).isFalse();
        assertThat(categoriesContaining(target)).isEmpty();
    }
}
