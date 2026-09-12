package com.swimming.backend.folder.service;

import com.swimming.backend.folder.domain.Folder;
import com.swimming.backend.folder.repository.FolderRepository;
import com.swimming.backend.folder.repository.entity.FolderEntity;
import com.swimming.backend.user.domain.User;
import com.swimming.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:folder-pin;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
class FolderPinIntegrationTest {

    @Autowired
    private FolderService folderService;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("폴더 고정은 관리 Entity의 변경 감지로 처리하고, 고정한 폴더가 목록 앞에 온다")
    void pinsFolderWithDirtyCheckingAndOrdersPinnedFoldersFirst() {
        User user = userRepository.saveAndFlush(User.builder()
                .email("folder-pin@example.com")
                .googleSubject("folder-pin-google-subject")
                .nickname("folder-pin-user")
                .timezone("Asia/Seoul")
                .build());
        FolderEntity older = saveFolder(user, "먼저 만든 폴더");
        FolderEntity newer = saveFolder(user, "나중에 만든 폴더");
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        // 고정 전에는 최근 생성 순이다.
        assertThat(folderService.getAll(user.getId())).extracting(Folder::getName)
                .containsExactly("나중에 만든 폴더", "먼저 만든 폴더");

        statistics.clear();
        Folder pinned = folderService.pin(user.getId(), older.getId(), true);

        // SELECT + UPDATE 두 번뿐이다. save를 부르지 않고 변경 감지로 쓴다.
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        assertThat(pinned.getPinnedAt()).isNotNull();
        assertThat(folderService.getAll(user.getId())).extracting(Folder::getName)
                .containsExactly("먼저 만든 폴더", "나중에 만든 폴더");

        folderService.pin(user.getId(), newer.getId(), true);

        // 둘 다 고정하면 최근에 고정한 폴더가 앞에 온다.
        assertThat(folderService.getAll(user.getId())).extracting(Folder::getName)
                .containsExactly("나중에 만든 폴더", "먼저 만든 폴더");

        folderService.pin(user.getId(), newer.getId(), false);

        assertThat(folderService.getAll(user.getId())).extracting(Folder::getName)
                .containsExactly("먼저 만든 폴더", "나중에 만든 폴더");
        assertThat(storedPinnedAt(user, newer)).isNull();
    }

    private FolderEntity saveFolder(User user, String name) {
        return folderRepository.saveAndFlush(FolderEntity.from(
                Folder.create(user.getId(), null, name, "설명", null),
                user,
                null
        ));
    }

    private java.time.Instant storedPinnedAt(User user, FolderEntity entity) {
        List<Folder> folders = folderService.getAll(user.getId());
        return folders.stream()
                .filter(folder -> folder.getId().equals(entity.getId()))
                .findFirst()
                .orElseThrow()
                .getPinnedAt();
    }
}
