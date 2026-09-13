package com.swimming.backend.knowledge.usecase;

import com.swimming.backend.folder.domain.Folder;
import com.swimming.backend.folder.repository.FolderRepository;
import com.swimming.backend.folder.repository.entity.FolderEntity;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.repository.KnowledgeSourceRepository;
import com.swimming.backend.user.domain.User;
import com.swimming.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:source-duplicate-lookup-probe;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=never",
        "spring.ai.openai.api-key=test",
        "app.knowledge.fetch.render.enabled=false",
        "app.place.background.cdn-base-url=https://cdn.example.com"
})
@Transactional(propagation = Propagation.REQUIRED)
class SourceDuplicateLookupQueryCountTest {

    private static final int SOURCE_COUNT = 10;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private KnowledgeSourceRepository sourceRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("여러 canonical URL의 기존 Source를 한 문장으로 조회한다")
    void 기존_Source를_배치_조회한다() {
        User user = userRepository.saveAndFlush(User.builder()
                .email("source-duplicate-probe@example.com")
                .googleSubject("source-duplicate-probe-google-subject")
                .nickname("source-duplicate-probe-user")
                .timezone("Asia/Seoul")
                .build());

        FolderEntity folder = folderRepository.saveAndFlush(FolderEntity.from(
                Folder.create(user.getId(), null, "폴더", "설명", null),
                user,
                null
        ));

        List<String> canonicalUrls = IntStream.range(0, SOURCE_COUNT)
                .mapToObj(index -> "https://example.com/" + index)
                .toList();

        canonicalUrls.forEach(url -> sourceRepository.save(KnowledgeSource.create(
                user.getId(), folder.getId(), "문서", url, url
        )));
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.clear();

        List<KnowledgeSource> sources = sourceRepository.findAllInFolderByCanonicalUrls(
                user.getId(), folder.getId(), canonicalUrls
        );

        assertThat(sources).hasSize(SOURCE_COUNT);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(statistics.getCollectionLoadCount()).isZero();
    }
}
