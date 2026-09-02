package com.swimming.backend.task.repository;

import com.swimming.backend.folder.domain.Folder;
import com.swimming.backend.folder.repository.FolderRepository;
import com.swimming.backend.folder.repository.entity.FolderEntity;
import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.repository.entity.TaskEntity;
import com.swimming.backend.user.domain.User;
import com.swimming.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:task-repository;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
class TaskRepositoryJoinTest {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("Task와 폴더 정보를 한 번의 조인 쿼리로 조회한다")
    void findsTasksWithFoldersInSingleQuery() {
        User user = userRepository.saveAndFlush(User.builder()
                .email("task-list@example.com")
                .googleSubject("task-repository-google-subject-1")
                .nickname("task-list-user")
                .timezone("Asia/Seoul")
                .build());
        FolderEntity folder = folderRepository.saveAndFlush(FolderEntity.from(
                Folder.create(user.getId(), null, "폴더", "설명", null),
                user,
                null
        ));
        TaskEntity savedTask = taskRepository.saveAndFlush(TaskEntity.from(
                Task.create(user.getId(), folder.getId(), "폴더 Task", 0),
                user,
                folder,
                null
        ));

        Statistics statistics = entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.clear();

        List<TaskEntity> tasks = taskRepository.findAllByFolderIdWithFolder(folder.getId());

        assertThat(tasks).extracting(TaskEntity::getId)
                .containsExactly(savedTask.getId());
        assertThat(tasks.getFirst().getFolder().getName()).isEqualTo("폴더");
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("soft delete된 Task는 일반 조회에서 제외하고 이력용 참조 조회에는 유지한다")
    @Transactional(propagation = Propagation.REQUIRED)
    void keepsSoftDeletedTaskForHistoricalReference() {
        User user = userRepository.saveAndFlush(User.builder()
                .email("soft-delete-task@example.com")
                .googleSubject("task-repository-google-subject-2")
                .nickname("soft-delete-task-user")
                .timezone("Asia/Seoul")
                .build());
        FolderEntity folder = folderRepository.saveAndFlush(FolderEntity.from(
                Folder.create(user.getId(), null, "폴더", "설명", null),
                user,
                null
        ));
        TaskEntity task = taskRepository.saveAndFlush(TaskEntity.from(
                Task.create(user.getId(), folder.getId(), "세션에 기록된 Task", 0),
                user,
                folder,
                null
        ));

        assertThat(taskRepository.softDeleteAllOwnedByIds(
                user.getId(),
                List.of(task.getId())
        )).isEqualTo(1);

        assertThat(taskRepository.findAllByFolderIdWithFolder(folder.getId())).isEmpty();
        assertThat(taskRepository.findByIdAndUser_IdAndDeletedFalse(
                task.getId(),
                user.getId()
        )).isEmpty();
        assertThat(taskRepository.findAllOwnedActiveByIds(
                user.getId(),
                List.of(task.getId())
        )).isEmpty();
        assertThat(taskRepository.findAllOwnedByIdsIncludingDeleted(
                user.getId(),
                List.of(task.getId())
        )).singleElement().satisfies(reference -> {
            assertThat(reference.id()).isEqualTo(task.getId());
            assertThat(reference.title()).isEqualTo("세션에 기록된 Task");
        });
    }

    @Test
    @DisplayName("Matrix 영역을 rank와 ID 커서 기준으로 페이지 조회한다")
    void pagesMatrixSectionByRankAndId() {
        User user = userRepository.saveAndFlush(User.builder()
                .email("matrix-page@example.com")
                .googleSubject("task-repository-google-subject-matrix")
                .nickname("matrix-user")
                .timezone("Asia/Seoul")
                .build());
        TaskEntity first = saveMatrixTask(user, "첫째", false, true, 3072L);
        TaskEntity second = saveMatrixTask(user, "둘째", false, true, 2048L);
        TaskEntity third = saveMatrixTask(user, "셋째", false, true, 1024L);
        saveMatrixTask(user, "다른 영역", false, false, 4096L);

        List<TaskEntity> firstPage = taskRepository.findMatrixFirstPage(
                user.getId(),
                false,
                true,
                PageRequest.of(0, 2)
        );
        List<TaskEntity> nextPage = taskRepository.findMatrixNextPage(
                user.getId(),
                false,
                true,
                second.getMatrixRank(),
                second.getId(),
                PageRequest.of(0, 2)
        );

        assertThat(firstPage).extracting(TaskEntity::getId)
                .containsExactly(first.getId(), second.getId());
        assertThat(nextPage).extracting(TaskEntity::getId)
                .containsExactly(third.getId());
    }

    private TaskEntity saveMatrixTask(
            User user,
            String title,
            boolean priority,
            boolean urgent,
            long matrixRank
    ) {
        return taskRepository.saveAndFlush(TaskEntity.from(
                Task.create(user.getId(), null, title, 0, priority, urgent, matrixRank),
                user,
                null,
                null
        ));
    }
}
