package com.swimming.backend.project.repository;

import com.swimming.backend.project.repository.entity.ProjectTagEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectTagRepository extends JpaRepository<ProjectTagEntity, Long> {

    List<ProjectTagEntity> findAllByUserIdOrderByNameAsc(Long userId);

    Optional<ProjectTagEntity> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndName(Long userId, String name);

    boolean existsByUserIdAndNameAndIdNot(Long userId, String name, Long id);
}
