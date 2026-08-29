package com.swimming.backend.project.repository;

import com.swimming.backend.project.repository.entity.ProjectTagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectTagRepository extends JpaRepository<ProjectTagEntity, Long> {

    List<ProjectTagEntity> findAllByUserIdOrderByNameAsc(Long userId);

    Optional<ProjectTagEntity> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndName(Long userId, String name);

    boolean existsByUserIdAndNameAndIdNot(Long userId, String name, Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM ProjectTagEntity tag
            WHERE tag.id = :tagId
              AND tag.userId = :userId
            """)
    int deleteOwnedTag(
            @Param("tagId") Long tagId,
            @Param("userId") Long userId
    );
}
