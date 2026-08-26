package com.swimming.backend.note.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.note.domain.Note;
import com.swimming.backend.note.domain.NoteContextType;
import com.swimming.backend.note.domain.NoteStatus;
import com.swimming.backend.note.repository.NoteRepository;
import com.swimming.backend.note.repository.entity.NoteEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NoteService {

    private final NoteRepository noteRepository;

    @Transactional(propagation = Propagation.REQUIRED)
    public Note create(Note note) {
        return toDomain(
                noteRepository.saveAndFlush(
                        NoteEntity.builder()
                                .userId(note.getUserId())
                                .content(note.getContent())
                                .status(NoteStatus.ACTIVE)
                                .deleted(false)
                                .contextType(note.getContextType())
                                .projectId(note.getProjectId())
                                .sessionId(note.getSessionId())
                                .build()
                )
        );
    }

    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public List<Note> getAll(
            Long userId,
            NoteStatus status
    ) {
        return noteRepository
                .findAllByUserIdAndStatusAndDeletedFalseOrderByUpdatedAtDesc(
                        userId,
                        status
                )
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public List<Note> getByContext(
            Long userId,
            NoteContextType contextType,
            NoteStatus status
    ) {
        return noteRepository
                .findAllByUserIdAndContextTypeAndStatusAndDeletedFalseOrderByUpdatedAtDesc(
                        userId,
                        contextType,
                        status
                )
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public List<Note> getByProject(
            Long userId,
            Long projectId,
            NoteStatus status
    ) {
        return noteRepository
                .findAllByUserIdAndProjectIdAndStatusAndDeletedFalseOrderByUpdatedAtDesc(
                        userId,
                        projectId,
                        status
                )
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public List<Note> getBySession(
            Long userId,
            Long sessionId,
            NoteStatus status
    ) {
        return noteRepository
                .findAllByUserIdAndSessionIdAndStatusAndDeletedFalseOrderByUpdatedAtDesc(
                        userId,
                        sessionId,
                        status
                )
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public Note getOne(
            Long userId,
            Long noteId,
            NoteStatus status
    ) {
        return toDomain(
                noteRepository
                        .findByIdAndUserIdAndStatusAndDeletedFalse(
                                noteId,
                                userId,
                                status
                        )
                        .orElseThrow(this::noteNotFound)
        );
    }

    @Transactional(
            propagation = Propagation.REQUIRED,
            readOnly = true
    )
    public Note getExisting(
            Long userId,
            Long noteId
    ) {
        return toDomain(getOwnedNoteEntity(userId, noteId));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Note update(Note note) {
        NoteEntity entity = getOwnedNoteEntity(
                note.getUserId(),
                note.getId()
        );

        entity.update(
                note.getContent(),
                note.getStatus(),
                note.isDeleted()
        );

        return toDomain(noteRepository.saveAndFlush(entity));
    }

    private NoteEntity getOwnedNoteEntity(
            Long userId,
            Long noteId
    ) {
        return noteRepository
                .findByIdAndUserIdAndDeletedFalse(
                        noteId,
                        userId
                )
                .orElseThrow(this::noteNotFound);
    }

    private BusinessException noteNotFound() {
        return new BusinessException(ErrorCode.NOTE_NOT_FOUND);
    }

    private Note toDomain(NoteEntity entity) {
        return Note.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .content(entity.getContent())
                .status(entity.getStatus())
                .deleted(entity.isDeleted())
                .projectId(entity.getProjectId())
                .sessionId(entity.getSessionId())
                .contextType(entity.getContextType())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
