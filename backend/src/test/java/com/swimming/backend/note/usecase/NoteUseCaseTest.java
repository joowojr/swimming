package com.swimming.backend.note.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.note.domain.Note;
import com.swimming.backend.note.domain.NoteContextType;
import com.swimming.backend.note.domain.NoteStatus;
import com.swimming.backend.note.dto.in.NoteCreateRequest;
import com.swimming.backend.note.dto.in.NoteCreateResponse;
import com.swimming.backend.note.dto.in.NoteResponse;
import com.swimming.backend.note.dto.in.NoteUpdateRequest;
import com.swimming.backend.note.service.NoteService;
import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.session.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoteUseCaseTest {

    private NoteService noteService;
    private FolderService folderService;
    private SessionService sessionService;
    private NoteUseCase noteUseCase;

    @BeforeEach
    void setUp() {
        noteService = mock(NoteService.class);
        folderService = mock(FolderService.class);
        sessionService = mock(SessionService.class);
        noteUseCase = new NoteUseCase(noteService, folderService, sessionService);
    }

    @Test
    @DisplayName("기본 Note를 생성하고 저장된 Note를 그대로 반환한다")
    void createsDefaultNote() {
        Note saved = note(1L, "메모", NoteStatus.ACTIVE, NoteContextType.DEFAULT, null, null);
        when(noteService.create(any(Note.class))).thenReturn(saved);

        NoteCreateResponse response = noteUseCase.create(
                1L,
                new NoteCreateRequest("메모", NoteContextType.DEFAULT, null, null)
        );

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.content()).isEqualTo("메모");
        assertThat(response.status()).isEqualTo(NoteStatus.ACTIVE);
        assertThat(response.contextType()).isEqualTo(NoteContextType.DEFAULT);
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-08-24T10:00:00Z"));
        assertThat(response.updatedAt()).isEqualTo(Instant.parse("2026-08-24T10:00:00Z"));
    }

    @Test
    @DisplayName("소유한 폴더에 Note를 생성한다")
    void createsFolderNoteAfterOwnershipCheck() {
        Note saved = note(2L, "폴더 메모", NoteStatus.ACTIVE, NoteContextType.FOLDER, 10L, null);
        when(noteService.create(any(Note.class))).thenReturn(saved);

        NoteCreateResponse response = noteUseCase.create(
                1L,
                new NoteCreateRequest("폴더 메모", NoteContextType.FOLDER, 10L, null)
        );

        verify(folderService).getReference(1L, 10L);
        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.folderId()).isEqualTo(10L);
        assertThat(response.content()).isEqualTo("폴더 메모");
    }

    @Test
    @DisplayName("소유한 세션에 Note를 생성한다")
    void createsSessionNoteAfterOwnershipCheck() {
        Note saved = note(3L, "세션 메모", NoteStatus.ACTIVE, NoteContextType.SESSION, null, 20L);
        when(noteService.create(any(Note.class))).thenReturn(saved);

        noteUseCase.create(
                1L,
                new NoteCreateRequest("세션 메모", NoteContextType.SESSION, null, 20L)
        );

        verify(sessionService).getOwned(1L, 20L);
    }

    @Test
    @DisplayName("Note 컨텍스트와 연결 ID 조합이 올바르지 않으면 생성하지 않는다")
    void rejectsInvalidCreateContext() {
        NoteCreateRequest request = new NoteCreateRequest(
                "잘못된 메모",
                NoteContextType.FOLDER,
                null,
                20L
        );

        assertThatThrownBy(() -> noteUseCase.create(1L, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_NOTE_CONTEXT));
        verify(noteService, never()).create(any(Note.class));
    }

    @Test
    @DisplayName("폴더 소유권을 확인한 뒤 폴더 Note를 조회한다")
    void returnsFolderNotesAfterOwnershipCheck() {
        when(noteService.getByFolder(1L, 10L, NoteStatus.ACTIVE)).thenReturn(List.of(
                note(1L, "메모", NoteStatus.ACTIVE, NoteContextType.FOLDER, 10L, null)
        ));

        List<NoteResponse> responses = noteUseCase.getAll(
                1L,
                NoteStatus.ACTIVE,
                null,
                10L,
                null
        );

        verify(folderService).getReference(1L, 10L);
        assertThat(responses).extracting(NoteResponse::id).containsExactly(1L);
    }

    @Test
    @DisplayName("목록 필터를 둘 이상 전달하면 조회하지 않는다")
    void rejectsMultipleListFilters() {
        assertThatThrownBy(() -> noteUseCase.getAll(
                1L,
                NoteStatus.ACTIVE,
                NoteContextType.FOLDER,
                10L,
                null
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_NOTE_CONTEXT));

        verify(noteService, never()).getByFolder(1L, 10L, NoteStatus.ACTIVE);
    }

    @Test
    @DisplayName("Note 내용을 도메인에서 변경한 뒤 저장한다")
    void updatesNoteContent() {
        Note note = note(1L, "기존", NoteStatus.ACTIVE, NoteContextType.DEFAULT, null, null);
        when(noteService.getOne(1L, 1L, NoteStatus.ACTIVE)).thenReturn(note);
        when(noteService.updateContent(note)).thenReturn(note);

        NoteResponse response = noteUseCase.update(
                1L,
                1L,
                new NoteUpdateRequest("수정")
        );

        assertThat(response.content()).isEqualTo("수정");
        verify(noteService).updateContent(note);
    }

    @Test
    @DisplayName("Note 보관 요청은 도메인을 보관 상태로 바꿔 저장한다")
    void archivesNote() {
        Note note = note(1L, "메모", NoteStatus.ACTIVE, NoteContextType.DEFAULT, null, null);
        when(noteService.getOne(1L, 1L, NoteStatus.ACTIVE)).thenReturn(note);

        noteUseCase.archive(1L, 1L);

        assertThat(note.getStatus()).isEqualTo(NoteStatus.ARCHIVED);
        verify(noteService).archive(note);
    }

    @Test
    @DisplayName("Note 복원 요청은 보관된 도메인을 활성 상태로 바꿔 저장한다")
    void restoresNote() {
        Note note = note(1L, "메모", NoteStatus.ARCHIVED, NoteContextType.DEFAULT, null, null);
        when(noteService.getOne(1L, 1L, NoteStatus.ARCHIVED)).thenReturn(note);

        noteUseCase.restore(1L, 1L);

        assertThat(note.getStatus()).isEqualTo(NoteStatus.ACTIVE);
        verify(noteService).restore(note);
    }

    @Test
    @DisplayName("Note 삭제 요청은 도메인을 삭제 상태로 바꿔 저장한다")
    void deletesNote() {
        Note note = note(1L, "메모", NoteStatus.ACTIVE, NoteContextType.DEFAULT, null, null);
        when(noteService.getExisting(1L, 1L)).thenReturn(note);

        noteUseCase.delete(1L, 1L);

        assertThat(note.isDeleted()).isTrue();
        verify(noteService).delete(note);
    }

    private Note note(
            Long id,
            String content,
            NoteStatus status,
            NoteContextType contextType,
            Long folderId,
            Long sessionId
    ) {
        return Note.restore(
                id,
                1L,
                content,
                status,
                false,
                contextType,
                folderId,
                sessionId,
                Instant.parse("2026-08-24T10:00:00Z"),
                Instant.parse("2026-08-24T10:00:00Z")
        );
    }
}
