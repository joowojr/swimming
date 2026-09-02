package com.swimming.backend.task.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.task.domain.TaskMatrixSection;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class TaskMatrixCursorCodec {

    private static final String VERSION = "v1";

    private TaskMatrixCursorCodec() {
    }

    public static String encode(TaskMatrixSection section, long matrixRank, long taskId) {
        String payload = String.join("|", VERSION, section.name(), Long.toString(matrixRank), Long.toString(taskId));
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public static DecodedCursor decode(String cursor, TaskMatrixSection expectedSection) {
        try {
            String payload = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] fields = payload.split("\\|", -1);
            if (fields.length != 4 || !VERSION.equals(fields[0])) {
                throw invalidCursor();
            }

            TaskMatrixSection section = TaskMatrixSection.valueOf(fields[1]);
            long matrixRank = Long.parseLong(fields[2]);
            long taskId = Long.parseLong(fields[3]);
            if (section != expectedSection || taskId <= 0) {
                throw invalidCursor();
            }
            return new DecodedCursor(matrixRank, taskId);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_MATRIX_CURSOR, exception);
        }
    }

    private static BusinessException invalidCursor() {
        return new BusinessException(ErrorCode.INVALID_MATRIX_CURSOR);
    }

    public record DecodedCursor(long matrixRank, long taskId) {
    }
}
