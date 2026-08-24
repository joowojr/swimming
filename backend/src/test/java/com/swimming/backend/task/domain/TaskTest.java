package com.swimming.backend.task.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskTest {

    @Test
    @DisplayName("Task를 생성하면 제목을 정규화하고 할 일 상태로 시작한다")
    void createsTodoTask() {
        Task task = Task.create(10L, " API 명세 작성 ", 3);

        assertThat(task.getProjectId()).isEqualTo(10L);
        assertThat(task.getTitle()).isEqualTo("API 명세 작성");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(task.getOrderIdx()).isEqualTo(3);
    }

    @Test
    @DisplayName("Task가 자신의 제목과 상태를 수정한다")
    void updatesTaskState() {
        Task task = Task.create(10L, "API 명세 작성", 0);

        task.update(" 수정 Task ", TaskStatus.HOLD);

        assertThat(task.getTitle()).isEqualTo("수정 Task");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.HOLD);
    }
}
