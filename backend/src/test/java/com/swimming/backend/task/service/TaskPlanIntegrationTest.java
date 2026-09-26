package com.swimming.backend.task.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.folder.domain.Folder;
import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.dto.projection.PlannedTaskRow;
import com.swimming.backend.user.domain.User;
import com.swimming.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 캘린더 날짜는 task의 속성이라 한 task는 최대 하나의 날짜를 갖는다. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:task-plan;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=never",
        "spring.ai.openai.api-key=test",
        "app.place.background.cdn-base-url=https://cdn.example.com"
})
@Transactional
class TaskPlanIntegrationTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 21);
    private static final LocalDate TUESDAY = MONDAY.plusDays(1);

    @Autowired
    private TaskService taskService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FolderService folderService;

    private Long userId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        userId = userRepository.saveAndFlush(User.builder()
                .email("task-plan-" + suffix + "@example.com")
                .googleSubject("task-plan-" + suffix)
                .nickname("task-plan-" + suffix.substring(0, 8))
                .timezone("Asia/Seoul")
                .build()).getId();
    }

    @Test
    @DisplayName("다른 날짜에 담긴 할 일을 담으면 그 날짜로 옮겨지고 원래 날짜에서는 빠진다")
    void movesTaskToNewDate() {
        Task task = taskService.create(userId, null, "보고서 작성");
        taskService.plan(userId, List.of(task.getId()), MONDAY);

        taskService.plan(userId, List.of(task.getId()), TUESDAY);

        assertThat(taskService.getPlannedRows(userId, MONDAY, MONDAY)).isEmpty();
        assertThat(taskService.getPlannedRows(userId, TUESDAY, TUESDAY))
                .extracting(PlannedTaskRow::taskId)
                .containsExactly(task.getId());
        assertThat(taskService.getOne(userId, task.getId()).getPlanDate()).isEqualTo(TUESDAY);
    }

    @Test
    @DisplayName("하루 안에서는 최근에 만든 할 일이 먼저 온다")
    void ordersByCreatedAtDescWithinDay() {
        Task older = taskService.create(userId, null, "먼저 만든 할 일");
        Task newer = taskService.create(userId, null, "나중에 만든 할 일");
        taskService.plan(userId, List.of(newer.getId()), MONDAY);
        taskService.plan(userId, List.of(older.getId()), MONDAY);

        assertThat(taskService.getPlannedRows(userId, MONDAY, MONDAY))
                .extracting(PlannedTaskRow::taskId)
                .containsExactly(newer.getId(), older.getId());
    }

    @Test
    @DisplayName("캘린더에서 빼면 날짜가 비고 할 일은 남는다")
    void unplansTask() {
        Task task = taskService.create(userId, null, "장보기");
        taskService.plan(userId, List.of(task.getId()), MONDAY);

        taskService.unplan(userId, task.getId(), MONDAY);

        assertThat(taskService.getOne(userId, task.getId()).getPlanDate()).isNull();
        assertThat(taskService.getPlannedRows(userId, MONDAY, MONDAY)).isEmpty();
    }

    @Test
    @DisplayName("다른 날짜에 담긴 할 일은 이 날짜에서 뺄 수 없다")
    void rejectsUnplanFromOtherDate() {
        Task task = taskService.create(userId, null, "장보기");
        taskService.plan(userId, List.of(task.getId()), MONDAY);

        assertThatThrownBy(() -> taskService.unplan(userId, task.getId(), TUESDAY))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DAILY_PLAN_ITEM_NOT_FOUND));
        assertThat(taskService.getOne(userId, task.getId()).getPlanDate()).isEqualTo(MONDAY);
    }

    @Test
    @DisplayName("그 날짜에 담긴 할 일만 센다")
    void countsOnlyTasksPlannedOnDate() {
        Task monday = taskService.create(userId, null, "월요일");
        Task tuesday = taskService.create(userId, null, "화요일");
        taskService.plan(userId, List.of(monday.getId()), MONDAY);
        taskService.plan(userId, List.of(tuesday.getId()), TUESDAY);

        assertThat(taskService.countPlannedOn(userId, MONDAY, List.of(monday.getId(), tuesday.getId())))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("폴더를 바꾸지 않는 수정은 날짜와 중요·즉시만 바꾸고 폴더 연결을 그대로 둔다")
    void keepsFolderWhenInfoUpdateDoesNotChangeFolder() {
        Folder folder = folderService.create(Folder.create(userId, null, "폴더", "설명", null));
        Task task = taskService.create(userId, folder.getId(), "보고서 작성");

        Task updated = taskService.updateInfo(userId, task.getId(), "보고서 작성", false, null, true, true, 2048L, MONDAY);

        assertThat(updated.getFolderId()).isEqualTo(folder.getId());
        assertThat(updated.isPriority()).isTrue();
        assertThat(updated.isUrgent()).isTrue();
        assertThat(updated.getPlanDate()).isEqualTo(MONDAY);
    }

    @Test
    @DisplayName("다른 사용자의 할 일은 캘린더에 담을 수 없다")
    void rejectsAnotherUsersTask() {
        Task task = taskService.create(userId, null, "내 할 일");

        assertThatThrownBy(() -> taskService.plan(userId + 1000, List.of(task.getId()), MONDAY))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND));
    }
}
