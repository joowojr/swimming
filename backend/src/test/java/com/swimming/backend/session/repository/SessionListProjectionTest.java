package com.swimming.backend.session.repository;

import com.swimming.backend.place.domain.BackgroundAssetType;
import com.swimming.backend.place.repository.CityRepository;
import com.swimming.backend.place.repository.PlaceRepository;
import com.swimming.backend.place.repository.entity.CityEntity;
import com.swimming.backend.place.repository.entity.PlaceEntity;
import com.swimming.backend.session.domain.Session;
import com.swimming.backend.session.repository.entity.SessionEntity;
import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.repository.TaskRepository;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:session-list-projection;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.sql.init.mode=never",
        "spring.ai.openai.api-key=test",
        "app.place.background.cdn-base-url=https://cdn.example.com"
})
class SessionListProjectionTest {

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SessionTaskRepository sessionTaskRepository;

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("SessionTask를 Task ID 순서로 Place와 City까지 한 번의 flat projection으로 조회한다")
    @Transactional(propagation = Propagation.REQUIRED)
    void findsSessionListRowsWithPlaceInOneQuery() {
        User user = userRepository.saveAndFlush(User.builder()
                .email("session-list-projection@example.com")
                .passwordHash("password")
                .nickname("session-list-projection-user")
                .timezone("Asia/Seoul")
                .build());
        CityEntity city = cityRepository.saveAndFlush(CityEntity.create("Lisbon", "PT", "Europe/Lisbon"));
        PlaceEntity place = placeRepository.saveAndFlush(PlaceEntity.create(
                city,
                "Alfama Cafe",
                BackgroundAssetType.VIDEO,
                "places/video/alfama.mp4",
                null
        ));
        TaskEntity firstTask = saveTask(user, "첫 Task", 0);
        TaskEntity secondTask = saveTask(user, "두 번째 Task", 1);
        SessionEntity session = sessionRepository.saveAndFlush(SessionEntity.from(
                Session.createPersonal(
                        user.getId(),
                        place.getId(),
                        List.of(firstTask.getId(), secondTask.getId()),
                        1500
                )
        ));
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        var rows = sessionRepository.findListRows(user.getId());

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(row -> row.sessionId()).containsOnly(session.getId());
        assertThat(rows).extracting(row -> row.taskId())
                .containsExactly(firstTask.getId(), secondTask.getId());
        assertThat(rows.getFirst().cityName()).isEqualTo("Lisbon");
        assertThat(rows.getFirst().placeName()).isEqualTo("Alfama Cafe");

        assertThat(sessionTaskRepository.completeAll(
                session.getId(),
                List.of(firstTask.getId())
        )).isEqualTo(1);
        assertThat(sessionRepository.findListRows(user.getId()))
                .extracting(row -> row.taskCompleted())
                .containsExactly(true, false);
    }

    private TaskEntity saveTask(User user, String title, int orderIdx) {
        return taskRepository.saveAndFlush(TaskEntity.from(
                Task.create(user.getId(), null, title, orderIdx),
                user,
                null,
                null
        ));
    }
}
