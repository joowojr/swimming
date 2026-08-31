package com.swimming.backend.task.repository;

import com.swimming.backend.task.repository.entity.TaskEntity;
import com.swimming.backend.project.domain.ProjectStatus;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.task.dto.projection.TaskOrganizerContextRow;
import com.swimming.backend.task.dto.projection.TaskReference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<TaskEntity, Long> {

    @Query("""
            SELECT task
            FROM TaskEntity task
            JOIN FETCH task.project project
            WHERE project.id = :projectId
              AND task.deleted = false
            ORDER BY task.createdAt DESC
            """)
    List<TaskEntity> findAllByProjectIdWithProject(@Param("projectId") Long projectId);

    List<TaskEntity> findAllByUser_IdAndDeletedFalseOrderByCreatedAtDesc(Long userId);

    List<TaskEntity> findAllByUser_IdAndProjectIsNullAndDeletedFalseOrderByCreatedAtDesc(Long userId);

    Optional<TaskEntity> findTopByProject_IdAndDeletedFalseOrderByIdDesc(Long projectId);

    Optional<TaskEntity> findTopByUser_IdAndProjectIsNullAndDeletedFalseOrderByOrderIdxDescIdDesc(Long userId);

    Optional<TaskEntity> findByIdAndUser_IdAndDeletedFalse(Long taskId, Long userId);

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
            """)
    List<TaskReference> findAllOwnedByIdsIncludingDeleted(
            @Param("userId") Long userId,
            @Param("taskIds") List<Long> taskIds
    );

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
              AND task.deleted = false
            """)
    List<TaskReference> findAllOwnedActiveByIds(
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
            LEFT JOIN TaskEntity task ON task.project = project AND task.deleted = false
            WHERE project.user.id = :userId
              AND project.status <> :excludedStatus
              AND project.deleted = false
            ORDER BY project.createdAt DESC, task.orderIdx ASC, task.id ASC
            """)
    List<TaskOrganizerContextRow> findTaskOrganizerContext(
            @Param("userId") Long userId,
            @Param("excludedStatus") ProjectStatus excludedStatus
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update TaskEntity task
            set task.deleted = true,
                task.updatedAt = CURRENT_TIMESTAMP
            where task.user.id = :userId
              and task.id in :taskIds
              and task.deleted = false
            """)
    int softDeleteAllOwnedByIds(@Param("userId") Long userId,
                                @Param("taskIds") List<Long> taskIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update TaskEntity task
            set task.status = :status,
                task.updatedAt = CURRENT_TIMESTAMP
            where task.user.id = :userId
              and task.id in :taskIds
            """)
    int updateOwnedStatuses(@Param("userId") Long userId,
                            @Param("taskIds") List<Long> taskIds,
                            @Param("status") TaskStatus status);
}
