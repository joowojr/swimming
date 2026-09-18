package com.swimming.backend.folder.repository;

import com.swimming.backend.folder.domain.FolderStatus;
import com.swimming.backend.folder.repository.entity.FolderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface FolderRepository extends JpaRepository<FolderEntity, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FolderEntity f where f.id = :folderId and f.user.id = :userId and f.deleted = false")
    Optional<FolderEntity> findOwnedForUpdate(@Param("userId") Long userId, @Param("folderId") Long folderId);

    /**
     * 고정한 폴더가 먼저 오고, 그 안에서는 최근에 고정한 순이다. 고정하지 않은 폴더는
     * 뒤에서 기존대로 최근 생성 순이다. NULLS LAST는 메서드 이름으로 쓸 수 없어 쿼리로 둔다.
     */
    @EntityGraph(attributePaths = "tag")
    @Query("""
            SELECT folder
            FROM FolderEntity folder
            WHERE folder.user.id = :userId
              AND folder.status <> :excludedStatus
              AND folder.deleted = false
            ORDER BY folder.pinnedAt DESC NULLS LAST, folder.createdAt DESC
            """)
    List<FolderEntity> findAllActiveOrderByPinnedAtDescCreatedAtDesc(
            @Param("userId") Long userId,
            @Param("excludedStatus") FolderStatus excludedStatus
    );

    @EntityGraph(attributePaths = "tag")
    Optional<FolderEntity> findByIdAndUser_IdAndDeletedFalse(Long id, Long userId);

    @Query("""
            SELECT count(task.id)
            FROM FolderEntity folder
            JOIN TaskEntity task ON task.folder.id = folder.id
            WHERE folder.id = :folderId
              AND folder.user.id = :userId
              AND folder.deleted = false
              AND task.deleted = false
            """)
    long countActiveTasks(
            @Param("userId") Long userId,
            @Param("folderId") Long folderId
    );

    @Query("""
            SELECT count(folder.id)
            FROM FolderEntity folder
            WHERE folder.user.id = :userId
              AND folder.id IN :folderIds
              AND folder.deleted = false
            """)
    long countOwnedActiveByIds(
            @Param("userId") Long userId,
            @Param("folderIds") Set<Long> folderIds
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE FolderEntity folder
            SET folder.tag = null
            WHERE folder.user.id = :userId
              AND folder.tag.id = :tagId
            """)
    int clearTagFromOwnedFolders(
            @Param("userId") Long userId,
            @Param("tagId") Long tagId
    );
}
