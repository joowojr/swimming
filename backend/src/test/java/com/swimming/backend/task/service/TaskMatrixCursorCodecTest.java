package com.swimming.backend.task.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.task.domain.TaskMatrixSection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskMatrixCursorCodecTest {

    @Test
    @DisplayName("Matrix 위치를 불투명 커서로 인코딩하고 복원한다")
    void encodesAndDecodesCursor() {
        String cursor = TaskMatrixCursorCodec.encode(TaskMatrixSection.URGENT, 2048L, 12L);

        TaskMatrixCursorCodec.DecodedCursor decoded = TaskMatrixCursorCodec.decode(
                cursor,
                TaskMatrixSection.URGENT
        );

        assertThat(decoded.matrixRank()).isEqualTo(2048L);
        assertThat(decoded.taskId()).isEqualTo(12L);
    }

    @Test
    @DisplayName("다른 영역에서 발급한 Matrix 커서를 거부한다")
    void rejectsCursorFromAnotherSection() {
        String cursor = TaskMatrixCursorCodec.encode(TaskMatrixSection.URGENT, 2048L, 12L);

        assertThatThrownBy(() -> TaskMatrixCursorCodec.decode(cursor, TaskMatrixSection.STANDARD))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_MATRIX_CURSOR));
    }

    @Test
    @DisplayName("해석할 수 없는 Matrix 커서를 거부한다")
    void rejectsMalformedCursor() {
        assertThatThrownBy(() -> TaskMatrixCursorCodec.decode("not-a-cursor", TaskMatrixSection.URGENT))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_MATRIX_CURSOR));
    }
}
