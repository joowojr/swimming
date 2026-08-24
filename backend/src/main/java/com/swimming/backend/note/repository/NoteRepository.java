package com.swimming.backend.note.repository;

import com.swimming.backend.note.domain.NoteContextType;
import com.swimming.backend.note.domain.NoteStatus;
import com.swimming.backend.note.repository.entity.NoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NoteRepository extends JpaRepository<NoteEntity, Long> {

    Optional<NoteEntity> findByIdAndUserIdAndStatusAndDeletedFalse(
            Long id,
            Long userId,
            NoteStatus status
    );

    Optional<NoteEntity> findByIdAndUserIdAndDeletedFalse(
            Long id,
            Long userId
    );

    List<NoteEntity> findAllByUserIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(
            Long userId,
            NoteStatus status
    );

    List<NoteEntity> findAllByUserIdAndContextTypeAndStatusAndDeletedFalseOrderByCreatedAtDesc(
            Long userId,
            NoteContextType contextType,
            NoteStatus status
    );

    List<NoteEntity> findAllByUserIdAndProjectIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(
            Long userId,
            Long projectId,
            NoteStatus status
    );

    List<NoteEntity> findAllByUserIdAndSessionIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(
            Long userId,
            Long sessionId,
            NoteStatus status
    );
}
