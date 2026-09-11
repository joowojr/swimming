package com.swimming.backend.note.dto.in;

import com.swimming.backend.note.domain.NoteContextType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 참조 범위를 좁히기 위한 컨텍스트를 함께 받는다.
 *
 * <ul>
 *   <li>{@code DEFAULT} — 아카이브를 제외한 사용자의 모든 폴더</li>
 *   <li>{@code FOLDER} — {@code contextId} 가 가리키는 폴더 하나</li>
 *   <li>{@code SESSION} — {@code contextId} 세션의 task 들이 속한 폴더들</li>
 * </ul>
 *
 * <p>{@code contextType} 이 null 이면 {@code DEFAULT} 로 본다.
 */
public record TaskOrganizeRequest(
        @NotNull Long noteId,
        String memo,
        NoteContextType contextType,
        Long contextId,
        @NotNull LocalDate currentDate
) {
    public NoteContextType contextTypeOrDefault() {
        return contextType == null ? NoteContextType.DEFAULT : contextType;
    }
}
