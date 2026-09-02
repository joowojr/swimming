package com.swimming.backend.folder.service;

import com.swimming.backend.folder.domain.Folder;
import com.swimming.backend.folder.domain.FolderStatus;
import com.swimming.backend.folder.domain.FolderTag;
import com.swimming.backend.folder.repository.FolderRepository;
import com.swimming.backend.folder.repository.FolderTagRepository;
import com.swimming.backend.folder.repository.entity.FolderEntity;
import com.swimming.backend.folder.repository.entity.FolderTagEntity;
import com.swimming.backend.user.domain.User;
import com.swimming.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:project-direct-mutation;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class FolderChangeDetectionIntegrationTest {

    @Autowired
    private FolderService folderService;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private FolderTagRepository folderTagRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("프로젝트 수정과 soft delete는 관리 Entity의 변경 감지로 처리한다")
    void updatesAndDeletesProjectWithDirtyChecking() {
        User user = userRepository.saveAndFlush(User.builder()
                .email("project-direct-mutation@example.com")
                .googleSubject("project-change-detection-google-subject")
                .nickname("project-direct-mutation-user")
                .timezone("Asia/Seoul")
                .build());
        FolderTagEntity tag = folderTagRepository.saveAndFlush(
                FolderTagEntity.from(FolderTag.create(user.getId(), "업무"))
        );
        FolderEntity entity = folderRepository.saveAndFlush(FolderEntity.from(
                Folder.create(user.getId(), null, "기존 프로젝트", "기존 설명", null),
                user,
                null
        ));
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        statistics.clear();
        Folder updated = folderService.update(
                user.getId(),
                entity.getId(),
                tag.getId(),
                "수정 프로젝트",
                "수정 설명",
                LocalDate.of(2026, 12, 31),
                FolderStatus.IN_PROGRESS
        );

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
        assertThat(updated.getUpdatedAt()).isNotNull();
        Folder stored = folderRepository
                .findByIdAndUser_IdAndDeletedFalse(entity.getId(), user.getId())
                .orElseThrow()
                .toDomain();
        assertThat(stored.getName()).isEqualTo("수정 프로젝트");
        assertThat(stored.getDescription()).isEqualTo("수정 설명");
        assertThat(stored.getTag().getId()).isEqualTo(tag.getId());
        assertThat(stored.getUpdatedAt()).isEqualTo(updated.getUpdatedAt());

        statistics.clear();
        folderService.delete(user.getId(), entity.getId());

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
        assertThat(folderRepository.findByIdAndUser_IdAndDeletedFalse(entity.getId(), user.getId()))
                .isEmpty();
    }
}
