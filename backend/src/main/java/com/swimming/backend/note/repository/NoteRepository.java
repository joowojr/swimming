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

    Optional<NoteEntity> findByIdAndUserId(Long id, Long userId);

    List<NoteEntity> findAllByUserIdAndStatusAndDeletedFalseOrderByUpdatedAtDesc(
            Long userId,
            NoteStatus status
    );

    List<NoteEntity> findAllByUserIdAndContextTypeAndStatusAndDeletedFalseOrderByUpdatedAtDesc(
            Long userId,
            NoteContextType contextType,
            NoteStatus status
    );

    List<NoteEntity> findAllByUserIdAndProjectIdAndStatusAndDeletedFalseOrderByUpdatedAtDesc(
            Long userId,
            Long projectId,
            NoteStatus status
    );

    List<NoteEntity> findAllByUserIdAndSessionIdAndStatusAndDeletedFalseOrderByUpdatedAtDesc(
            Long userId,
            Long sessionId,
            NoteStatus status
    );
}
