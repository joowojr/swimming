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

    @EntityGraph(attributePaths = "tag")
    List<FolderEntity> findAllByUser_IdAndStatusNotAndDeletedFalseOrderByCreatedAtDesc(
            Long userId,
            FolderStatus excludedStatus
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
