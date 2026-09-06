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
import org.springframework.data.domain.Sort;

import java.time.Instant;
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
    List<TaskEntity> findAllByFolderIdWithFolder(@Param("folderId") Long folderId);

    /**
     * 폴더의 할 일 첫 페이지. 커서에 싣는 값과 같은 (생성 시각, id) 순서를 써야 경계에서
     * 행이 겹치거나 빠지지 않는다.
     *
     * <p>다음 페이지를 같은 쿼리로 합치지 않는다. {@code :cursor IS NULL} 로 묶으면 그
     * 파라미터가 비교 없이 홀로 놓여 PostgreSQL이 타입을 정하지 못한다
     * ({@code could not determine data type of parameter}).
     */
    @Query("""
            SELECT task
            FROM TaskEntity task
            JOIN FETCH task.folder folder
            WHERE folder.id = :folderId
              AND task.deleted = false
            ORDER BY task.createdAt DESC, task.id DESC
            """)
    List<TaskEntity> findFolderTaskFirstPage(
            @Param("folderId") Long folderId,
            Pageable pageable
    );

    @Query("""
            SELECT task
            FROM TaskEntity task
            JOIN FETCH task.folder folder
            WHERE folder.id = :folderId
              AND task.deleted = false
              AND (
                    task.createdAt < :cursorCreatedAt
                 OR (task.createdAt = :cursorCreatedAt AND task.id < :cursorTaskId)
              )
            ORDER BY task.createdAt DESC, task.id DESC
            """)
    List<TaskEntity> findFolderTaskNextPage(
            @Param("folderId") Long folderId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorTaskId") Long cursorTaskId,
            Pageable pageable
    );

    /**
     * 폴더 진척은 화면에 보이는 페이지가 아니라 폴더 전체를 기준으로 센다.
     * 목록을 한 장씩 끊어 읽어도 "12/20 완료"는 그대로여야 한다.
     */
    long countByFolder_IdAndDeletedFalse(Long folderId);

    long countByFolder_IdAndDeletedFalseAndStatus(Long folderId, TaskStatus status);

    List<TaskEntity> findAllByUser_IdAndDeletedFalse(Long userId, Sort sort);

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
              AND (:status IS NULL OR task.status = :status)
            ORDER BY task.matrixRank DESC, task.id DESC
            """)
    List<TaskEntity> findMatrixFirstPage(
            @Param("userId") Long userId,
            @Param("priority") boolean priority,
            @Param("urgent") boolean urgent,
            @Param("status") TaskStatus status,
            Pageable pageable
    );

    @Query("""
            SELECT task
            FROM TaskEntity task
            WHERE task.user.id = :userId
              AND task.deleted = false
              AND task.priority = :priority
              AND task.urgent = :urgent
              AND (:status IS NULL OR task.status = :status)
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
            @Param("status") TaskStatus status,
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

    Optional<TaskEntity> findTopByFolder_IdAndDeletedFalseOrderByIdDesc(Long folderId);

    Optional<TaskEntity> findTopByUser_IdAndFolderIsNullAndDeletedFalseOrderByOrderIdxDescIdDesc(Long userId);

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
            FROM FolderEntity folder
            LEFT JOIN TaskEntity task ON task.folder = folder AND task.deleted = false
            WHERE folder.user.id = :userId
              AND folder.status <> :excludedStatus
              AND folder.deleted = false
            ORDER BY folder.createdAt DESC, task.createdAt DESC
            """)
    List<TaskOrganizerContextRow> findTaskOrganizerContext(
            @Param("userId") Long userId,
            @Param("excludedStatus") FolderStatus excludedStatus
    );

    /**
     * 명시적으로 지정된 폴더만 대상으로 하는 컨텍스트.
     *
     * <p>전체 조회와 달리 ARCHIVED 를 걸러내지 않는다. 사용자가 그 폴더를 직접
     * 골랐거나 세션이 그 폴더의 task 를 담고 있는 상황이라, 상태를 이유로 빼면
     * 참조 대상이 사라진다.
     */
    @Query("""
            SELECT new com.swimming.backend.task.dto.projection.TaskOrganizerContextRow(
                folder.id,
                folder.name,
                folder.description,
                task.id,
                task.title,
                task.status
            )
            FROM FolderEntity folder
            LEFT JOIN TaskEntity task ON task.folder = folder AND task.deleted = false
            WHERE folder.user.id = :userId
              AND folder.id IN :folderIds
              AND folder.deleted = false
            ORDER BY folder.createdAt DESC, task.createdAt DESC
            """)
    List<TaskOrganizerContextRow> findTaskOrganizerContextByFolderIds(
            @Param("userId") Long userId,
            @Param("folderIds") List<Long> folderIds
    );

    @Query("""
            SELECT DISTINCT task.folder.id
            FROM TaskEntity task
            WHERE task.user.id = :userId
              AND task.id IN :taskIds
              AND task.folder IS NOT NULL
              AND task.deleted = false
            """)
    List<Long> findFolderIdsByTaskIds(
            @Param("userId") Long userId,
            @Param("taskIds") List<Long> taskIds
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update TaskEntity task
            set task.deleted = true,
                task.updatedAt = instant
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
                task.updatedAt = instant
            where task.user.id = :userId
              and task.id in :taskIds
            """)
    int updateOwnedStatuses(@Param("userId") Long userId,
                            @Param("taskIds") List<Long> taskIds,
                            @Param("status") TaskStatus status);
}
