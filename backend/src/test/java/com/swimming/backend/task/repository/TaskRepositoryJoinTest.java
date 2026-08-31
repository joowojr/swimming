package com.swimming.backend.task.repository;

import com.swimming.backend.project.domain.Project;
import com.swimming.backend.project.repository.ProjectRepository;
import com.swimming.backend.project.repository.entity.ProjectEntity;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:task-repository;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.sql.init.mode=never",
        "spring.ai.openai.api-key=test",
        "app.place.background.cdn-base-url=https://cdn.example.com"
})
class TaskRepositoryJoinTest {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("Task와 폴더 정보를 한 번의 조인 쿼리로 조회한다")
    void findsTasksWithProjectsInSingleQuery() {
        User user = userRepository.saveAndFlush(User.builder()
                .email("task-list@example.com")
                .passwordHash("password")
                .nickname("task-list-user")
                .timezone("Asia/Seoul")
                .build());
        ProjectEntity project = projectRepository.saveAndFlush(ProjectEntity.from(
                Project.create(user.getId(), null, "폴더", "설명", null),
                user,
                null
        ));
        TaskEntity savedTask = taskRepository.saveAndFlush(TaskEntity.from(
                Task.create(user.getId(), project.getId(), "폴더 Task", 0),
                user,
                project,
                null
        ));

        Statistics statistics = entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.clear();

        List<TaskEntity> tasks = taskRepository.findAllByProjectIdWithProject(project.getId());

        assertThat(tasks).extracting(TaskEntity::getId)
                .containsExactly(savedTask.getId());
        assertThat(tasks.getFirst().getProject().getName()).isEqualTo("폴더");
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }
}
