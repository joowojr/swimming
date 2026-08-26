package com.swimming.backend.task.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskTest {

    @Test
    @DisplayName("Task를 생성하면 제목을 정규화하고 할 일 상태로 시작한다")
    void createsTodoTask() {
        Task task = Task.create(1L, 10L, " API 명세 작성 ", 3);

        assertThat(task.getUserId()).isEqualTo(1L);
        assertThat(task.getProjectId()).isEqualTo(10L);
        assertThat(task.getSourceNoteId()).isNull();
        assertThat(task.getTitle()).isEqualTo("API 명세 작성");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(task.getOrderIdx()).isEqualTo(3);
    }

    @Test
    @DisplayName("Note에서 생성한 Task는 원문 Note ID를 보관한다")
    void createsTaskFromSourceNote() {
        Task task = Task.createFromNote(1L, 10L, 7L, "API 명세 작성", 3);

        assertThat(task.getSourceNoteId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("Task가 자신의 제목을 앞뒤 공백 없이 수정한다")
    void changesTitleOnly() {
        Task task = Task.create(1L, 10L, "API 명세 작성", 0);
        task.changeStatus(TaskStatus.DOING);

        task.changeTitle(" 수정 Task ");

        assertThat(task.getTitle()).isEqualTo("수정 Task");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.DOING);
    }

    @Test
    @DisplayName("Task가 제목을 유지한 채 상태만 수정한다")
    void changesStatusOnly() {
        Task task = Task.create(1L, 10L, "API 명세 작성", 0);

        task.changeStatus(TaskStatus.HOLD);

        assertThat(task.getTitle()).isEqualTo("API 명세 작성");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.HOLD);
    }
}
