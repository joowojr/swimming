package com.swimming.backend.task.repository;

import com.swimming.backend.task.repository.entity.TaskEntity;
import com.swimming.backend.project.domain.ProjectStatus;
import com.swimming.backend.task.dto.projection.TaskOrganizerContextRow;
import com.swimming.backend.task.dto.projection.TaskReference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<TaskEntity, Long> {

    List<TaskEntity> findAllByProject_IdOrderByOrderIdxAscIdAsc(Long projectId);

    Optional<TaskEntity> findTopByProject_IdOrderByOrderIdxDescIdDesc(Long projectId);

    Optional<TaskEntity> findTopByUser_IdAndProjectIsNullOrderByOrderIdxDescIdDesc(Long userId);

    Optional<TaskEntity> findByIdAndUser_Id(Long taskId, Long userId);

    @Query("""
            SELECT new com.swimming.backend.task.dto.projection.TaskReference(
                task.id,
                project.id,
                project.name,
                task.title,
                task.status
            )
            FROM TaskEntity task
            LEFT JOIN task.project project
            WHERE task.user.id = :userId
              AND task.id IN :taskIds
              AND (project.id IS NULL OR project.deleted = false)
            """)
    List<TaskReference> findAllOwnedByIds(
            @Param("userId") Long userId,
            @Param("taskIds") List<Long> taskIds
    );

    @Query("""
            SELECT task
            FROM TaskEntity task
            WHERE task.user.id = :userId
              AND task.id IN :taskIds
            """)
    List<TaskEntity> findAllOwnedEntitiesByIds(
            @Param("userId") Long userId,
            @Param("taskIds") List<Long> taskIds
    );

    @Query("""
            SELECT new com.swimming.backend.task.dto.projection.TaskOrganizerContextRow(
                project.id,
                project.name,
                project.description,
                task.id,
                task.title,
                task.status
            )
            FROM ProjectEntity project
            LEFT JOIN TaskEntity task ON task.project = project
            WHERE project.user.id = :userId
              AND project.status <> :excludedStatus
              AND project.deleted = false
            ORDER BY project.createdAt DESC, task.orderIdx ASC, task.id ASC
            """)
    List<TaskOrganizerContextRow> findTaskOrganizerContext(
            @Param("userId") Long userId,
            @Param("excludedStatus") ProjectStatus excludedStatus
    );
}
