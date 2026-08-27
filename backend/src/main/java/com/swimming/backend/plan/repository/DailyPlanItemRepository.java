package com.swimming.backend.plan.repository;

import com.swimming.backend.plan.repository.entity.DailyPlanItemEntity;
import com.swimming.backend.plan.repository.projection.DailyPlanItemQueryRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyPlanItemRepository extends JpaRepository<DailyPlanItemEntity, Long> {
    List<DailyPlanItemEntity> findAllByUserIdAndPlanDateOrderByOrderIdxAsc(Long userId, LocalDate planDate);

    Optional<DailyPlanItemEntity> findByIdAndUserIdAndPlanDate(Long id, Long userId, LocalDate planDate);

    @Query("""
            select new com.swimming.backend.plan.repository.projection.DailyPlanItemQueryRow(
                item.id,
                item.planDate,
                item.taskId,
                project.id,
                project.name,
                project.deleted,
                task.title,
                task.status,
                item.orderIdx
            )
            from DailyPlanItemEntity item
            join TaskEntity task on task.id = item.taskId
            left join task.project project
            where item.userId = :userId
              and task.user.id = :userId
              and item.planDate between :fromDate and :toDate
            order by item.planDate asc, item.orderIdx asc
            """)
    List<DailyPlanItemQueryRow> findRows(@Param("userId") Long userId,
                                         @Param("fromDate") LocalDate fromDate,
                                         @Param("toDate") LocalDate toDate);

    @Query("""
            select (count(item) > 0) from DailyPlanItemEntity item
            where item.userId = :userId and item.planDate = :planDate and item.taskId = :taskId
            """)
    boolean containsTask(@Param("userId") Long userId,
                         @Param("planDate") LocalDate planDate,
                         @Param("taskId") Long taskId);
}
