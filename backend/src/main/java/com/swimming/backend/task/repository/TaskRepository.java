package com.swimming.backend.task.repository;

import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.dto.projection.TaskReference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findAllByProjectIdOrderByOrderIdxAscIdAsc(Long projectId);

    Optional<Task> findTopByProjectIdOrderByOrderIdxDescIdDesc(Long projectId);

    @Query("""
            SELECT new com.swimming.backend.task.dto.projection.TaskReference(
                task.id,
                task.projectId,
                project.name,
                task.title,
                task.status,
                task.completionPct
            )
            FROM Task task
            JOIN ProjectEntity project ON project.id = task.projectId
            WHERE project.userId = :userId
              AND task.id IN :taskIds
            """)
    List<TaskReference> findAllOwnedByIds(
            @Param("userId") Long userId,
            @Param("taskIds") List<Long> taskIds
    );
}
