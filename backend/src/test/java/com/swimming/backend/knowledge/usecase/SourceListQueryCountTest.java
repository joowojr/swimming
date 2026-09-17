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
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.IntStream;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:source-list-probe;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=never",
        "spring.ai.openai.api-key=test",
        "app.place.background.cdn-base-url=https://cdn.example.com"
})
@Transactional(propagation = Propagation.REQUIRED)
/**
 * 폴더 링크 목록이 페이지 크기와 무관하게 고정된 문장 수를 쓰는지 지킨다.
 *
 * <p>Source마다 관계를 읽거나 제목을 읽으면 목록 한 번에 왕복이 그만큼 늘어난다. 묶어 읽는
 * 구조가 깨지면 이 테스트가 먼저 걸린다.
 */
class SourceListQueryCountTest {

    private static final int SOURCE_COUNT = 20;
    private static final int SUBJECTS_PER_SOURCE = 3;

    @Autowired
    private SourceQueryUseCase queryUseCase;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private KnowledgeSourceRepository sourceRepository;

    @Autowired
    private KnowledgeNodeRepository nodeRepository;

    @Autowired
    private KnowledgeRelationService relationService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("카테고리를 포함한 목록 조회는 페이지 크기와 무관하게 여섯 문장으로 끝난다")
    void 목록_조회는_문장수가_고정이다() {
        User user = userRepository.saveAndFlush(User.builder()
                .email("source-list-probe@example.com")
                .googleSubject("source-list-probe-google-subject")
                .nickname("source-list-probe-user")
                .timezone("Asia/Seoul")
                .build());

        FolderEntity folder = folderRepository.saveAndFlush(FolderEntity.from(
                Folder.create(user.getId(), null, "폴더", "설명", null),
                user,
                null
        ));

        KnowledgeNode category = nodeRepository.create(KnowledgeNode.create(
                user.getId(), NodeType.CATEGORY, "API 설계", null));
        IntStream.range(0, SOURCE_COUNT).forEach(sourceIndex -> {
            KnowledgeSource source = sourceRepository.save(KnowledgeSource.create(
                    user.getId(),
                    folder.getId(),
                    "문서 " + sourceIndex,
                    "https://example.com/" + sourceIndex,
                    "https://example.com/" + sourceIndex
            ));

            List<KnowledgeNode> subjects = IntStream.range(0, SUBJECTS_PER_SOURCE)
                    .mapToObj(subjectIndex -> nodeRepository.create(KnowledgeNode.create(
                            user.getId(), NodeType.SUBJECT,
                            "개념 " + sourceIndex + "-" + subjectIndex, null
                    )))
                    .toList();
            KnowledgeNode topic = nodeRepository.create(KnowledgeNode.create(
                    user.getId(), NodeType.TOPIC, "목적 " + sourceIndex, null
            ));

            relationService.connectAll(source.getNode(), subjects, RelationOrigin.AI);
            relationService.connect(source.getNode(), topic, RelationOrigin.AI);
            relationService.connect(category, source.getNode(), RelationOrigin.USER);
        });
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();
        for (int pageSize : List.of(1, SOURCE_COUNT)) {
            entityManager.clear();
            statistics.clear();
            var page = queryUseCase.list(user.getId(), folder.getId(), null, pageSize, null);

            assertThat(page.items()).hasSize(pageSize);
            assertThat(page.items()).allSatisfy(item -> {
                assertThat(item.subjects()).hasSize(SUBJECTS_PER_SOURCE);
                assertThat(item.category().nodeId()).isEqualTo(category.getId());
                assertThat(item.category().title()).isEqualTo("API 설계");
            });

            // 폴더 1 + Source 페이지 1 + Source 노드 1 + 정방향 관계 1 + 카테고리 관계 1 + 제목 1
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(6);
            assertThat(statistics.getCollectionLoadCount()).isZero();
        }
    }
}
