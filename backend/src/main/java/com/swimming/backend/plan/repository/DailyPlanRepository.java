package com.swimming.backend.plan.repository;

import com.swimming.backend.plan.repository.entity.DailyPlanEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyPlanRepository extends JpaRepository<DailyPlanEntity, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<DailyPlanEntity> findByUserIdAndPlanDate(Long userId, LocalDate planDate);

    @EntityGraph(attributePaths = "items")
    List<DailyPlanEntity> findAllByUserIdAndPlanDateBetweenOrderByPlanDateAsc(
            Long userId,
            LocalDate fromDate,
            LocalDate toDate
    );

    @Query("""
            select (count(item) > 0)
            from DailyPlanEntity dailyPlan
            join dailyPlan.items item
            where dailyPlan.userId = :userId
              and dailyPlan.planDate = :planDate
              and item.taskId = :taskId
            """)
    boolean containsTask(
            @Param("userId") Long userId,
            @Param("planDate") LocalDate planDate,
            @Param("taskId") Long taskId
    );
}
