package com.swimming.backend.project.repository;

import com.swimming.backend.project.domain.ProjectTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectTagRepository extends JpaRepository<ProjectTag, Long> {

    List<ProjectTag> findAllByUserIdOrderByNameAsc(Long userId);

    Optional<ProjectTag> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndName(Long userId, String name);
}
