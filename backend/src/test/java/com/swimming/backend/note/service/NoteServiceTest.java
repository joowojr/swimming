package com.swimming.backend.note.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.note.domain.Note;
import com.swimming.backend.note.domain.NoteContextType;
import com.swimming.backend.note.domain.NoteStatus;
import com.swimming.backend.note.repository.NoteRepository;
import com.swimming.backend.note.repository.entity.NoteEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoteServiceTest {

    private NoteRepository noteRepository;
    private NoteService noteService;

    @BeforeEach
    void setUp() {
        noteRepository = mock(NoteRepository.class);
        noteService = new NoteService(noteRepository);
    }

    @Test
    @DisplayName("세션 Note를 연결 ID와 감사 시간까지 저장해 도메인으로 반환한다")
    void createsSessionNoteWithContextAndTimestamps() {
        Instant createdAt = Instant.parse("2026-08-24T10:00:00Z");
        when(noteRepository.saveAndFlush(any(NoteEntity.class))).thenAnswer(invocation -> {
            NoteEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 1L);
            ReflectionTestUtils.setField(entity, "createdAt", createdAt);
            ReflectionTestUtils.setField(entity, "updatedAt", createdAt);
            return entity;
        });

        Note created = noteService.create(Note.createSession(1L, 20L, "세션 메모"));

        assertThat(created.getId()).isEqualTo(1L);
        assertThat(created.getSessionId()).isEqualTo(20L);
        assertThat(created.getContextType()).isEqualTo(NoteContextType.SESSION);
        assertThat(created.getCreatedAt()).isEqualTo(createdAt);
        assertThat(created.getUpdatedAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("활성 상태인 사용자 소유 Note만 상세 조회한다")
    void returnsOnlyOwnedActiveNote() {
        NoteEntity entity = entity(1L, 1L, "메모", NoteStatus.ACTIVE);
        when(noteRepository.findByIdAndUserIdAndStatusAndDeletedFalse(1L, 1L, NoteStatus.ACTIVE))
                .thenReturn(Optional.of(entity));

        Note note = noteService.getOne(1L, 1L, NoteStatus.ACTIVE);

        assertThat(note.getId()).isEqualTo(1L);
        assertThat(note.getStatus()).isEqualTo(NoteStatus.ACTIVE);
    }

    @Test
    @DisplayName("보관됐거나 다른 사용자의 Note는 찾을 수 없음으로 처리한다")
    void rejectsUnavailableNote() {
        when(noteRepository.findByIdAndUserIdAndStatusAndDeletedFalse(1L, 1L, NoteStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.getOne(1L, 1L, NoteStatus.ACTIVE))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOTE_NOT_FOUND));
    }

    @Test
    @DisplayName("활성 Note 목록을 최신 생성 순서 조회 결과대로 반환한다")
    void returnsActiveNotesInRepositoryOrder() {
        NoteEntity latest = entity(2L, 1L, "최신", NoteStatus.ACTIVE);
        NoteEntity older = entity(1L, 1L, "이전", NoteStatus.ACTIVE);
        when(noteRepository.findAllByUserIdAndStatusAndDeletedFalseOrderByUpdatedAtDesc(
                1L,
                NoteStatus.ACTIVE
        ))
                .thenReturn(List.of(latest, older));

        List<Note> notes = noteService.getAll(1L, NoteStatus.ACTIVE);

        assertThat(notes).extracting(Note::getId).containsExactly(2L, 1L);
    }

    @Test
    @DisplayName("Note 내용을 반영하고 즉시 저장한다")
    void updatesAndFlushesNote() {
        Note note = Note.restore(
                1L,
                1L,
                "수정",
                NoteStatus.ARCHIVED,
                true,
                NoteContextType.DEFAULT,
                null,
                null,
                null,
                null
        );

        NoteEntity entity = entity(1L, 1L, "기존", NoteStatus.ACTIVE);
        when(noteRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(entity));

        Note updated = noteService.updateContent(note);

        assertThat(updated.getContent()).isEqualTo("수정");
        assertThat(updated.getStatus()).isEqualTo(NoteStatus.ACTIVE);
        assertThat(updated.isDeleted()).isFalse();
        verify(noteRepository).findByIdAndUserId(1L, 1L);
        verify(noteRepository).flush();
    }

    private NoteEntity entity(
            Long id,
            Long userId,
            String content,
            NoteStatus status
    ) {
        NoteEntity entity = NoteEntity.builder()
                .userId(userId)
                .content(content)
                .status(status)
                .contextType(NoteContextType.DEFAULT)
                .build();
        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-08-24T10:00:00Z"));
        ReflectionTestUtils.setField(entity, "updatedAt", Instant.parse("2026-08-24T10:00:00Z"));
        return entity;
    }
}
