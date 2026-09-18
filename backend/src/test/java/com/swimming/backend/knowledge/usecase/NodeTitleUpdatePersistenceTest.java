package com.swimming.backend.knowledge.usecase;

import com.swimming.backend.folder.domain.Folder;
import com.swimming.backend.folder.repository.FolderRepository;
import com.swimming.backend.folder.repository.entity.FolderEntity;
import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.domain.NodeType;
import com.swimming.backend.knowledge.domain.RelationOrigin;
import com.swimming.backend.knowledge.repository.KnowledgeNodeRepository;
import com.swimming.backend.knowledge.repository.KnowledgeSourceRepository;
import com.swimming.backend.knowledge.service.data.KnowledgeRelationService;
import com.swimming.backend.user.domain.User;
import com.swimming.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:node-title-update;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=never",
        "spring.ai.openai.api-key=test",
        "app.place.background.cdn-base-url=https://cdn.example.com"
})
@Transactional(propagation = Propagation.REQUIRED)
class NodeTitleUpdatePersistenceTest {
    @Autowired private NodeUseCase nodeUseCase;
    @Autowired private SourceQueryUseCase sourceQueryUseCase;
    @Autowired private KnowledgeGraphUseCase graphUseCase;
    @Autowired private UserRepository userRepository;
    @Autowired private FolderRepository folderRepository;
    @Autowired private KnowledgeNodeRepository nodeRepository;
    @Autowired private KnowledgeSourceRepository sourceRepository;
    @Autowired private KnowledgeRelationService relationService;
    @Autowired private EntityManager entityManager;

    @Test
    void 단건_이동은_다른_문서의_카테고리를_유지하고_마지막_이동_후_빈_카테고리를_삭제한다() {
        User user = userRepository.saveAndFlush(User.builder()
                .email("category-move@example.com").googleSubject("category-move-google")
                .nickname("카테고리 이동").timezone("Asia/Seoul").build());
        FolderEntity folder = folderRepository.saveAndFlush(FolderEntity.from(
                Folder.create(user.getId(), null, "폴더", "설명", null), user, null));
        KnowledgeSource moving = sourceRepository.save(KnowledgeSource.create(
                user.getId(), folder.getId(), "이동", "https://example.com/moving", "https://example.com/moving"));
        KnowledgeSource staying = sourceRepository.save(KnowledgeSource.create(
                user.getId(), folder.getId(), "유지", "https://example.com/staying", "https://example.com/staying"));
        KnowledgeNode from = nodeRepository.create(KnowledgeNode.create(user.getId(), NodeType.CATEGORY, "이전 분류", null));
        relationService.connectAll(from, java.util.List.of(moving.getNode(), staying.getNode()), RelationOrigin.USER);
        KnowledgeSource existing = sourceRepository.save(KnowledgeSource.create(
                user.getId(), folder.getId(), "대상 문서", "https://example.com/existing", "https://example.com/existing"));
        KnowledgeNode into = nodeRepository.create(KnowledgeNode.create(user.getId(), NodeType.CATEGORY, "새 분류", null));
        relationService.connect(into, existing.getNode(), RelationOrigin.USER);
        entityManager.flush();
        entityManager.clear();

        var renamed = nodeUseCase.updateTitle(user.getId(), from.getId(), "변경한 분류");
        entityManager.flush();
        entityManager.clear();
        assertThat(renamed.nodeId()).isEqualTo(from.getId());
        assertThat(sourceQueryUseCase.get(user.getId(), moving.getId()).category()).isEqualTo(renamed);
        assertThat(sourceQueryUseCase.get(user.getId(), staying.getId()).category()).isEqualTo(renamed);

        var target = nodeUseCase.merge(user.getId(), from.getId(), into.getId(), moving.getId());
        entityManager.flush();
        entityManager.clear();
        assertThat(sourceQueryUseCase.get(user.getId(), moving.getId()).category()).isEqualTo(target);
        assertThat(sourceQueryUseCase.get(user.getId(), staying.getId()).category().nodeId()).isEqualTo(from.getId());
        assertThat(nodeRepository.findById(from.getId()).orElseThrow().getTitle()).isEqualTo("변경한 분류");

        var existingTarget = nodeUseCase.merge(user.getId(), from.getId(), into.getId(), staying.getId());
        entityManager.flush();
        entityManager.clear();
        assertThat(existingTarget).isEqualTo(target);
        assertThat(nodeRepository.findById(from.getId())).isEmpty();
        assertThat(sourceQueryUseCase.get(user.getId(), staying.getId()).category()).isEqualTo(target);
        assertThat(graphUseCase.ofFolder(user.getId(), folder.getId(), 50).nodes())
                .noneMatch(node -> node.nodeId().equals(from.getId()));
    }

    @Test
    void 제목_수정은_변경_감지로_저장되고_문서와_그래프에_반영된다() {
        User user = userRepository.saveAndFlush(User.builder()
                .email("node-title@example.com").googleSubject("node-title-google")
                .nickname("이름 수정").timezone("Asia/Seoul").build());
        FolderEntity folder = folderRepository.saveAndFlush(FolderEntity.from(
                Folder.create(user.getId(), null, "폴더", "설명", null), user, null));
        KnowledgeSource source = sourceRepository.save(KnowledgeSource.create(
                user.getId(), folder.getId(), "문서", "https://example.com", "https://example.com"));
        KnowledgeNode category = nodeRepository.create(KnowledgeNode.create(user.getId(), NodeType.CATEGORY, "이전 분류", null));
        KnowledgeNode topic = nodeRepository.create(KnowledgeNode.create(user.getId(), NodeType.TOPIC, "이전 목적", null));
        relationService.connect(category, source.getNode(), RelationOrigin.USER);
        relationService.connect(source.getNode(), topic, RelationOrigin.AI);
        entityManager.flush();
        entityManager.clear();

        assertThat(nodeRepository.findById(category.getId()).orElseThrow().getTitleRenamedAt()).isNull();
        nodeUseCase.updateTitle(user.getId(), category.getId(), "  새 분류  ");
        nodeUseCase.updateTitle(user.getId(), topic.getId(), "새 목적");
        entityManager.flush();
        entityManager.clear();

        assertThat(nodeRepository.findById(category.getId()).orElseThrow().getNormalizedTitle()).isEqualTo("새분류");
        var renamedAt = nodeRepository.findById(category.getId()).orElseThrow().getTitleRenamedAt();
        assertThat(renamedAt).isNotNull();
        assertThat(nodeRepository.findById(topic.getId()).orElseThrow().getTitleRenamedAt()).isNotNull();
        nodeUseCase.updateTitle(user.getId(), category.getId(), "  새 분류  ");
        entityManager.flush();
        entityManager.clear();
        assertThat(nodeRepository.findById(category.getId()).orElseThrow().getTitleRenamedAt()).isEqualTo(renamedAt);
        var detail = sourceQueryUseCase.get(user.getId(), source.getId());
        assertThat(detail.category().title()).isEqualTo("새 분류");
        assertThat(detail.topic().title()).isEqualTo("새 목적");
        var graph = graphUseCase.ofFolder(user.getId(), folder.getId(), 50);
        assertThat(graph.nodes()).anySatisfy(node -> {
            assertThat(node.nodeId()).isEqualTo(category.getId());
            assertThat(node.title()).isEqualTo("새 분류");
        }).anySatisfy(node -> {
            assertThat(node.nodeId()).isEqualTo(topic.getId());
            assertThat(node.title()).isEqualTo("새 목적");
        });
        assertThat(graph.edges()).hasSize(2);
    }
}
