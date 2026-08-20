package com.swimming.backend.task.repository;

import com.swimming.backend.task.domain.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findAllByProjectIdOrderByOrderIdxAscIdAsc(Long projectId);

    Optional<Task> findTopByProjectIdOrderByOrderIdxDescIdDesc(Long projectId);
}
