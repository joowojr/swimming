package com.swimming.backend.project.repository;

import com.swimming.backend.project.domain.ProjectStatus;
import com.swimming.backend.project.repository.entity.ProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ProjectRepository extends JpaRepository<ProjectEntity, Long> {

    @EntityGraph(attributePaths = "tag")
    List<ProjectEntity> findAllByUser_IdAndStatusNotAndDeletedFalseOrderByCreatedAtDesc(
            Long userId,
            ProjectStatus excludedStatus
    );

    @EntityGraph(attributePaths = "tag")
    Optional<ProjectEntity> findByIdAndUser_IdAndDeletedFalse(Long id, Long userId);

    @Query("""
            SELECT count(task.id)
            FROM ProjectEntity project
            JOIN TaskEntity task ON task.project.id = project.id
            WHERE project.id = :projectId
              AND project.user.id = :userId
              AND project.deleted = false
              AND task.deleted = false
            """)
    long countActiveTasks(
            @Param("userId") Long userId,
            @Param("projectId") Long projectId
    );

    @Query("""
            SELECT count(project.id)
            FROM ProjectEntity project
            WHERE project.user.id = :userId
              AND project.id IN :projectIds
              AND project.deleted = false
            """)
    long countOwnedActiveByIds(
            @Param("userId") Long userId,
            @Param("projectIds") Set<Long> projectIds
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ProjectEntity project
            SET project.tag = null
            WHERE project.user.id = :userId
              AND project.tag.id = :tagId
            """)
    int clearTagFromOwnedProjects(
            @Param("userId") Long userId,
            @Param("tagId") Long tagId
    );
}
