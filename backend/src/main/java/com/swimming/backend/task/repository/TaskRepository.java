package com.swimming.backend.task.repository;

import com.swimming.backend.task.repository.entity.TaskEntity;
import com.swimming.backend.folder.domain.FolderStatus;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.task.dto.projection.TaskOrganizerContextRow;
import com.swimming.backend.task.dto.projection.TaskReference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<TaskEntity, Long> {

    @Query("""
            SELECT task
            FROM TaskEntity task
            JOIN FETCH task.folder folder
            WHERE folder.id = :folderId
              AND task.deleted = false
            ORDER BY task.createdAt DESC
            """)
    List<TaskEntity> findAllByProjectIdWithProject(@Param("folderId") Long folderId);

    List<TaskEntity> findAllByUser_IdAndDeletedFalseOrderByCreatedAtDesc(Long userId);

    List<TaskEntity> findAllByUser_IdAndProjectIsNullAndDeletedFalseOrderByCreatedAtDesc(Long userId);

    Optional<TaskEntity> findTopByUser_IdAndDeletedFalseAndPriorityAndUrgentOrderByMatrixRankDescIdDesc(
            Long userId,
            boolean priority,
            boolean urgent
    );

    @Query("""
            SELECT task
            FROM TaskEntity task
            WHERE task.user.id = :userId
              AND task.deleted = false
              AND task.priority = :priority
              AND task.urgent = :urgent
            ORDER BY task.matrixRank DESC, task.id DESC
            """)
    List<TaskEntity> findMatrixFirstPage(
            @Param("userId") Long userId,
            @Param("priority") boolean priority,
            @Param("urgent") boolean urgent,
            Pageable pageable
    );

    @Query("""
            SELECT task
            FROM TaskEntity task
            WHERE task.user.id = :userId
              AND task.deleted = false
              AND task.priority = :priority
              AND task.urgent = :urgent
              AND (
                task.matrixRank < :cursorRank
                OR (task.matrixRank = :cursorRank AND task.id < :cursorTaskId)
              )
            ORDER BY task.matrixRank DESC, task.id DESC
            """)
    List<TaskEntity> findMatrixNextPage(
            @Param("userId") Long userId,
            @Param("priority") boolean priority,
            @Param("urgent") boolean urgent,
            @Param("cursorRank") long cursorRank,
            @Param("cursorTaskId") long cursorTaskId,
            Pageable pageable
    );

    List<TaskEntity> findAllByUser_IdAndDeletedFalseAndPriorityAndUrgentOrderByMatrixRankDescIdDesc(
            Long userId,
            boolean priority,
            boolean urgent
    );

    List<TaskEntity> findAllByUser_IdAndDeletedFalseAndIdIn(Long userId, List<Long> taskIds);

    Optional<TaskEntity> findTopByProject_IdAndDeletedFalseOrderByIdDesc(Long folderId);

    Optional<TaskEntity> findTopByUser_IdAndProjectIsNullAndDeletedFalseOrderByOrderIdxDescIdDesc(Long userId);

    Optional<TaskEntity> findByIdAndUser_IdAndDeletedFalse(Long taskId, Long userId);

    @Query("""
            SELECT new com.swimming.backend.task.dto.projection.TaskReference(
                task.id,
                folder.id,
                folder.name,
                task.title,
                task.status
            )
            FROM TaskEntity task
            LEFT JOIN task.folder folder
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
                folder.id,
                folder.name,
                task.title,
                task.status
            )
            FROM TaskEntity task
            LEFT JOIN task.folder folder
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
                folder.id,
                folder.name,
                folder.description,
                task.id,
                task.title,
                task.status
            )
            FROM ProjectEntity folder
            LEFT JOIN TaskEntity task ON task.folder = folder AND task.deleted = false
            WHERE folder.user.id = :userId
              AND folder.status <> :excludedStatus
              AND folder.deleted = false
            ORDER BY folder.createdAt DESC, task.orderIdx ASC, task.id ASC
            """)
    List<TaskOrganizerContextRow> findTaskOrganizerContext(
            @Param("userId") Long userId,
            @Param("excludedStatus") FolderStatus excludedStatus
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
