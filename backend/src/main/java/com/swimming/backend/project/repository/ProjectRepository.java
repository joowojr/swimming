package com.swimming.backend.project.repository;

import com.swimming.backend.project.domain.Project;
import com.swimming.backend.project.domain.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    @EntityGraph(attributePaths = "tag")
    List<Project> findAllByUserIdAndStatusNotOrderByCreatedAtDesc(
            Long userId,
            ProjectStatus excludedStatus
    );

    @EntityGraph(attributePaths = "tag")
    Optional<Project> findByIdAndUserId(Long id, Long userId);
}
