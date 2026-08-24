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
import com.swimming.backend.project.service.ProjectService;
import com.swimming.backend.session.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NoteUseCase {

    private final NoteService noteService;
    private final ProjectService projectService;
    private final SessionService sessionService;

    public NoteCreateResponse create(
            Long userId,
            NoteCreateRequest request
    ) {
        Note note = switch (request.contextType()) {

            case DEFAULT -> createDefault(
                    userId,
                    request
            );

            case PROJECT -> createProject(
                    userId,
                    request
            );

            case SESSION -> createSession(
                    userId,
                    request
            );
        };

        Note created = noteService.create(note);
        return new NoteCreateResponse(created.getId());
    }

    public List<NoteResponse> getAll(
            Long userId,
            NoteStatus status,
            NoteContextType contextType,
            Long projectId,
            Long sessionId
    ) {
        validateListFilters(contextType, projectId, sessionId);

        List<Note> notes;

        if (projectId != null) {
            validateProjectContext(
                    userId,
                    projectId
            );

            notes = noteService.getByProject(
                    userId,
                    projectId,
                    status
            );

        } else if (sessionId != null) {
            validateSessionContext(
                    userId,
                    sessionId
            );

            notes = noteService.getBySession(
                    userId,
                    sessionId,
                    status
            );

        } else if (contextType != null) {
            notes = noteService.getByContext(
                    userId,
                    contextType,
                    status
            );

        } else {
            notes = noteService.getAll(userId, status);
        }

        return notes.stream()
                .map(this::toResponse)
                .toList();
    }

    public NoteResponse getOne(
            Long userId,
            Long noteId
    ) {
        return toResponse(
                noteService.getOne(
                        userId,
                        noteId,
                        NoteStatus.ACTIVE
                )
        );
    }

    public NoteResponse update(
            Long userId,
            Long noteId,
            NoteUpdateRequest request
    ) {
        Note note = noteService.getOne(
                userId,
                noteId,
                NoteStatus.ACTIVE
        );

        /*
         * 변경의 주체는 Domain.
         */
        note.updateContent(request.content());

        return toResponse(
                noteService.update(note)
        );
    }

    public void archive(
            Long userId,
            Long noteId
    ) {
        Note note = noteService.getOne(
                userId,
                noteId,
                NoteStatus.ACTIVE
        );

        note.archive();

        noteService.update(note);
    }

    public void restore(
            Long userId,
            Long noteId
    ) {
        Note note = noteService.getOne(
                userId,
                noteId,
                NoteStatus.ARCHIVED
        );

        note.restoreFromArchive();

        noteService.update(note);
    }

    public void delete(
            Long userId,
            Long noteId
    ) {
        Note note = noteService.getExisting(
                userId,
                noteId
        );

        note.delete();

        noteService.update(note);
    }

    private Note createDefault(
            Long userId,
            NoteCreateRequest request
    ) {
        if (request.projectId() != null
                || request.sessionId() != null) {
            throw new BusinessException(
                    ErrorCode.INVALID_NOTE_CONTEXT
            );
        }

        return Note.createDefault(
                userId,
                request.content()
        );
    }

    private Note createProject(
            Long userId,
            NoteCreateRequest request
    ) {
        if (request.projectId() == null
                || request.sessionId() != null) {
            throw new BusinessException(
                    ErrorCode.INVALID_NOTE_CONTEXT
            );
        }

        validateProjectContext(
                userId,
                request.projectId()
        );

        return Note.createProject(
                userId,
                request.projectId(),
                request.content()
        );
    }

    private Note createSession(
            Long userId,
            NoteCreateRequest request
    ) {
        if (request.sessionId() == null
                || request.projectId() != null) {
            throw new BusinessException(
                    ErrorCode.INVALID_NOTE_CONTEXT
            );
        }

        validateSessionContext(
                userId,
                request.sessionId()
        );

        return Note.createSession(
                userId,
                request.sessionId(),
                request.content()
        );
    }

    private void validateProjectContext(
            Long userId,
            Long projectId
    ) {
        projectService.validateOwnership(
                userId,
                projectId
        );
    }

    private void validateSessionContext(
            Long userId,
            Long sessionId
    ) {
        sessionService.validateOwnership(
                userId,
                sessionId
        );
    }

    private void validateListFilters(
            NoteContextType contextType,
            Long projectId,
            Long sessionId
    ) {
        int filterCount = 0;
        if (contextType != null) {
            filterCount++;
        }
        if (projectId != null) {
            filterCount++;
        }
        if (sessionId != null) {
            filterCount++;
        }

        if (filterCount > 1) {
            throw new BusinessException(ErrorCode.INVALID_NOTE_CONTEXT);
        }
    }

    private NoteResponse toResponse(Note note) {
        return new NoteResponse(
                note.getId(),
                note.getContent(),
                note.getStatus(),
                note.getContextType(),
                note.getProjectId(),
                note.getSessionId(),
                note.getCreatedAt(),
                note.getUpdatedAt()
        );
    }
}
