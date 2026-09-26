package com.swimming.backend.task.dto.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 할 일의 정보를 한 번에 바꾼다. 사용자에게는 수정 모달 하나의 저장이라 한 트랜잭션으로 처리한다.
 *
 * <p>folderId는 보냈는지와 값을 함께 본다. 필드가 없으면(null) 폴더를 바꾸지 않고,
 * {@code null}을 보내면(빈 Optional) 미분류로, 값을 보내면 그 폴더로 옮긴다.
 * 폴더를 모르는 화면(매트릭스에서 이미 있는 할 일 담기)이 폴더 연결을 끊지 않게 하려는 구분이다.
 *
 * <p>planDate는 null이 "캘린더에 없음"을 뜻해 생략과 구분되지 않으므로 항상 보낸다.
 */
public record UpdateTaskInfoRequest(
        @NotBlank(message = "Task 제목을 입력해 주세요")
        @Size(max = 255, message = "Task 제목은 255자 이하여야 합니다")
        String title,

        @JsonDeserialize(using = PresenceAwareIdDeserializer.class)
        Optional<Long> folderId,

        @NotNull(message = "중요 여부를 입력해 주세요")
        Boolean priority,

        @NotNull(message = "즉시 여부를 입력해 주세요")
        Boolean urgent,

        LocalDate planDate
) {
    /** folderId 필드를 보냈으면 폴더를 바꾼다. */
    public boolean changesFolder() {
        return folderId != null;
    }

    /** 옮길 폴더. 미분류면 null이다. changesFolder()가 true일 때만 뜻이 있다. */
    public Long targetFolderId() {
        return folderId == null ? null : folderId.orElse(null);
    }
}
