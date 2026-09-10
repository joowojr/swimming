package com.swimming.backend.note.repository;

import com.swimming.backend.note.repository.entity.TaskOrganizerRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TaskOrganizerRunRepository extends JpaRepository<TaskOrganizerRunEntity, Long> {

    Optional<TaskOrganizerRunEntity> findByIdAndUserIdAndNoteId(
            Long id,
            Long userId,
            Long noteId
    );

}
