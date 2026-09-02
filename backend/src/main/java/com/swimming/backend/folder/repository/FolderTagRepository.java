package com.swimming.backend.folder.repository;

import com.swimming.backend.folder.repository.entity.FolderTagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FolderTagRepository extends JpaRepository<FolderTagEntity, Long> {

    List<FolderTagEntity> findAllByUserIdOrderByNameAsc(Long userId);

    Optional<FolderTagEntity> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndName(Long userId, String name);

    boolean existsByUserIdAndNameAndIdNot(Long userId, String name, Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM FolderTagEntity tag
            WHERE tag.id = :tagId
              AND tag.userId = :userId
            """)
    int deleteOwnedTag(
            @Param("tagId") Long tagId,
            @Param("userId") Long userId
    );
}
