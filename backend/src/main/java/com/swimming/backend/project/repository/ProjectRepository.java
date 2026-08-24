package com.swimming.backend.project.repository;

import com.swimming.backend.project.domain.ProjectStatus;
import com.swimming.backend.project.repository.entity.ProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<ProjectEntity, Long> {

    @EntityGraph(attributePaths = "tag")
    List<ProjectEntity> findAllByUser_IdAndStatusNotOrderByCreatedAtDesc(
            Long userId,
            ProjectStatus excludedStatus
    );

    @EntityGraph(attributePaths = "tag")
    Optional<ProjectEntity> findByIdAndUser_Id(Long id, Long userId);
}
