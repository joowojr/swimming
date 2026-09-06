package com.swimming.backend.task.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * 할 일 목록의 다음 페이지 위치를 문자열 하나로 감싼다.
 *
 * <p>정렬 기준이 생성 시각인데 한 번에 여러 건을 만들면 시각이 겹친다. 시각만으로는 경계에서
 * 행이 겹치거나 빠지므로 id를 함께 싣는다.
 */
public final class TaskCursorCodec {

    private static final String VERSION = "v1";

    private TaskCursorCodec() {
    }

    public static String encode(Instant createdAt, Long taskId) {
        String payload = String.join("|", VERSION, createdAt.toString(), taskId.toString());

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public static Decoded decode(String cursor) {
        try {
            String payload = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] fields = payload.split("\\|", -1);

            if (fields.length != 3 || !VERSION.equals(fields[0])) {
                throw new BusinessException(ErrorCode.INVALID_TASK_CURSOR);
            }

            return new Decoded(Instant.parse(fields[1]), Long.parseLong(fields[2]));
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_TASK_CURSOR);
        }
    }

    public record Decoded(Instant createdAt, Long taskId) {
    }
}
