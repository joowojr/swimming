package com.swimming.backend.task.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.domain.TaskMatrixSection;
import com.swimming.backend.task.domain.TaskOrderingScope;
import com.swimming.backend.task.domain.TaskPlacement;
import com.swimming.backend.task.domain.TaskPlacementChange;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.task.dto.in.TaskMatrixPageQuery;
import com.swimming.backend.task.dto.in.TaskPlacementRequest;
import com.swimming.backend.task.dto.TaskPlacementResult;
import com.swimming.backend.task.dto.out.TaskMatrixPageResponse;
import com.swimming.backend.task.dto.out.TaskPlacementResponse;
import com.swimming.backend.task.service.TaskMatrixCursorCodec;
import com.swimming.backend.task.service.TaskOrderingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskOrderingUseCaseTest {

    private TaskOrderingService taskService;
    private TaskOrderingUseCase taskOrderingUseCase;

    @BeforeEach
    void setUp() {
        taskService = mock(TaskOrderingService.class);
        taskOrderingUseCase = new TaskOrderingUseCase(taskService);
    }

    @Test
    @DisplayName("상태 파라미터를 주면 조회 조건으로 함께 넘긴다")
    void passesStatusFilterToMatrixQuery() {
        when(taskService.getMatrixPage(
                1L,
                TaskMatrixSection.URGENT,
                null,
                null,
                TaskStatus.DOING,
                21
        )).thenReturn(List.of());

        taskOrderingUseCase.getMatrixPage(1L, TaskMatrixPageQuery.from("urgent", 20, null, "doing"));

        verify(taskService).getMatrixPage(1L, TaskMatrixSection.URGENT, null, null, TaskStatus.DOING, 21);
    }

    @Test
    @DisplayName("Matrix 첫 페이지는 size보다 하나 더 조회해 다음 커서를 계산한다")
    void returnsFirstMatrixPageWithNextCursor() {
        Task first = matrixTask(3L, "첫째", false, true, 3072L);
        Task second = matrixTask(2L, "둘째", false, true, 2048L);
        Task lookAhead = matrixTask(1L, "다음 페이지", false, true, 1024L);
        when(taskService.getMatrixPage(
                1L,
                TaskMatrixSection.URGENT,
                null,
                null,
                null,
                3
        )).thenReturn(List.of(first, second, lookAhead));

        TaskMatrixPageResponse response = taskOrderingUseCase.getMatrixPage(
                1L,
                TaskMatrixPageQuery.from("urgent", 2, null, null)
        );

        assertThat(response.section()).isEqualTo(TaskMatrixSection.URGENT);
        assertThat(response.items()).extracting(item -> item.id()).containsExactly(3L, 2L);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isEqualTo(response.items().getLast().positionCursor());
        TaskMatrixCursorCodec.DecodedCursor decoded = TaskMatrixCursorCodec.decode(
                response.nextCursor(),
                TaskMatrixSection.URGENT
        );
        assertThat(decoded.matrixRank()).isEqualTo(2048L);
        assertThat(decoded.taskId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Matrix 다음 페이지 요청은 커서 위치를 복원해 조회한다")
    void restoresCursorForNextMatrixPage() {
        String cursor = TaskMatrixCursorCodec.encode(TaskMatrixSection.STANDARD, 2048L, 2L);
        when(taskService.getMatrixPage(
                1L,
                TaskMatrixSection.STANDARD,
                2048L,
                2L,
                null,
                21
        )).thenReturn(List.of(matrixTask(1L, "마지막", false, false, 1024L)));

        TaskMatrixPageResponse response = taskOrderingUseCase.getMatrixPage(
                1L,
                TaskMatrixPageQuery.from("standard", 20, cursor, null)
        );

        assertThat(response.items()).singleElement().satisfies(item ->
                assertThat(item.id()).isEqualTo(1L));
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
        verify(taskService).getMatrixPage(
                1L,
                TaskMatrixSection.STANDARD,
                2048L,
                2L,
                null,
                21
        );
    }

    @Test
    @DisplayName("Task 이동 결과를 새 위치 커서와 함께 반환한다")
    void returnsMovedTaskWithPositionCursor() {
        Task moved = matrixTask(4L, "이동", false, true, 2048L);
        TaskPlacement placement = mock(TaskPlacement.class);
        TaskPlacementChange change = new TaskPlacementChange(
                moved,
                TaskMatrixSection.STANDARD,
                TaskMatrixSection.URGENT,
                List.of(matrixTask(3L, "위", false, true, 3072L))
        );
        when(taskService.preparePlacement(
                1L, 4L, TaskMatrixSection.URGENT, 3L, 2L, TaskStatus.DONE
        )).thenReturn(placement);
        when(placement.move()).thenReturn(change);
        when(taskService.applyPlacement(1L, change)).thenReturn(new TaskPlacementResult(
                moved,
                TaskMatrixSection.STANDARD,
                TaskMatrixSection.URGENT,
                true
        ));

        TaskPlacementResponse response = taskOrderingUseCase.move(
                1L,
                4L,
                new TaskPlacementRequest("matrix", "urgent", 3L, 2L, TaskStatus.DONE)
        );

        assertThat(response.scope()).isEqualTo(TaskOrderingScope.MATRIX);
        assertThat(response.sourceSection()).isEqualTo(TaskMatrixSection.STANDARD);
        assertThat(response.targetSection()).isEqualTo(TaskMatrixSection.URGENT);
        assertThat(response.rebalancedSections()).containsExactly(TaskMatrixSection.URGENT);
        assertThat(response.task().id()).isEqualTo(4L);
        TaskMatrixCursorCodec.DecodedCursor cursor = TaskMatrixCursorCodec.decode(
                response.task().positionCursor(),
                TaskMatrixSection.URGENT
        );
        assertThat(cursor.matrixRank()).isEqualTo(2048L);
        assertThat(cursor.taskId()).isEqualTo(4L);
    }

    @Test
    @DisplayName("지원하지 않는 Task 정렬 범위는 이동 요청으로 처리하지 않는다")
    void rejectsUnsupportedOrderingScope() {
        assertThatThrownBy(() -> taskOrderingUseCase.move(
                1L,
                4L,
                new TaskPlacementRequest("calendar", "urgent", 3L, 2L, null)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TASK_PLACEMENT));
    }

    private Task matrixTask(
            Long id,
            String title,
            boolean priority,
            boolean urgent,
            long matrixRank
    ) {
        return Task.restore(
                id,
                1L,
                null,
                null,
                title,
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
