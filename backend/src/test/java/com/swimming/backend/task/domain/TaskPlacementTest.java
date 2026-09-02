package com.swimming.backend.task.domain;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskPlacementTest {

    @Test
    @DisplayName("Task를 두 이웃 사이로 이동하며 대상 영역을 함께 변경한다")
    void movesTaskBetweenAdjacentTasks() {
        Task moving = task(4L, false, false, 4096L);
        Task previous = task(3L, false, true, 3072L);
        Task next = task(2L, false, true, 1024L);
        TaskPlacement placement = TaskPlacement.prepare(
                moving,
                TaskMatrixSection.URGENT,
                List.of(previous, next),
                3L,
                2L
        );

        TaskPlacementChange change = placement.move();

        assertThat(change.sourceSection()).isEqualTo(TaskMatrixSection.STANDARD);
        assertThat(change.targetSection()).isEqualTo(TaskMatrixSection.URGENT);
        assertThat(change.movedTask().isPriority()).isFalse();
        assertThat(change.movedTask().isUrgent()).isTrue();
        assertThat(change.movedTask().getMatrixRank()).isEqualTo(2048L);
        assertThat(change.rebalanced()).isFalse();
    }

    @Test
    @DisplayName("같은 영역에서 이동 대상 자신을 제외한 첫 위치를 계산한다")
    void movesTaskToTopWithinSameSection() {
        Task next = task(3L, true, false, 3072L);
        Task moving = task(2L, true, false, 2048L);
        Task last = task(1L, true, false, 1024L);
        TaskPlacement placement = TaskPlacement.prepare(
                moving,
                TaskMatrixSection.PRIORITY,
                List.of(next, moving, last),
                null,
                3L
        );

        TaskPlacementChange change = placement.move();

        assertThat(change.movedTask().getMatrixRank()).isEqualTo(4096L);
        assertThat(change.sourceSection()).isEqualTo(TaskMatrixSection.PRIORITY);
        assertThat(change.rebalanced()).isFalse();
    }

    @Test
    @DisplayName("두 이웃의 rank 간격이 없으면 대상 영역을 재배치한다")
    void rebalancesTargetSectionWhenRankGapIsExhausted() {
        Task moving = task(4L, false, false, 4096L);
        Task previous = task(3L, false, true, 1025L);
        Task next = task(2L, false, true, 1024L);
        TaskPlacement placement = TaskPlacement.prepare(
                moving,
                TaskMatrixSection.URGENT,
                List.of(previous, next),
                3L,
                2L
        );

        TaskPlacementChange change = placement.move();

        assertThat(previous.getMatrixRank()).isEqualTo(2048L);
        assertThat(next.getMatrixRank()).isEqualTo(1024L);
        assertThat(change.movedTask().getMatrixRank()).isEqualTo(1536L);
        assertThat(change.rebalanced()).isTrue();
        assertThat(change.rebalancedTasks()).containsExactly(previous, next);
    }

    @Test
    @DisplayName("이웃 Task가 실제로 인접하지 않으면 이동 충돌로 거부한다")
    void rejectsMoveWhenAnchorsAreNotAdjacent() {
        Task moving = task(4L, false, false, 4096L);
        Task previous = task(3L, false, true, 3072L);
        Task between = task(2L, false, true, 2048L);
        Task next = task(1L, false, true, 1024L);

        assertThatThrownBy(() -> TaskPlacement.prepare(
                moving,
                TaskMatrixSection.URGENT,
                List.of(previous, between, next),
                3L,
                1L
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TASK_PLACEMENT_CONFLICT));
    }

    private Task task(Long id, boolean priority, boolean urgent, long matrixRank) {
        return Task.restore(
                id,
                1L,
                null,
                null,
                "Task " + id,
                TaskStatus.TODO,
                priority,
                urgent,
                0,
                matrixRank,
                null,
                null
        );
    }
}
