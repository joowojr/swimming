package com.swimming.backend.calendar.repository;

import com.swimming.backend.calendar.repository.entity.DailyPlanItemEntity;
import com.swimming.backend.calendar.dto.projection.DailyPlanItemQueryRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface DailyPlanItemRepository extends JpaRepository<DailyPlanItemEntity, Long> {
    /** 담은 순으로 읽는다. 사용자가 순서를 바꾸는 기능은 없다. */
    List<DailyPlanItemEntity> findAllByUserIdAndPlanDateOrderByIdAsc(Long userId, LocalDate planDate);

    @Query("""
            select new com.swimming.backend.calendar.dto.projection.DailyPlanItemQueryRow(
                item.id,
                item.planDate,
                item.taskId,
                folder.id,
                folder.name,
                folder.deleted,
                task.title,
                task.status,
                task.priority,
                task.urgent
            )
            from DailyPlanItemEntity item
            join TaskEntity task on task.id = item.taskId
            left join task.folder folder
            where item.userId = :userId
              and task.user.id = :userId
              and task.deleted = false
              and item.planDate between :fromDate and :toDate
            order by item.planDate asc, item.createdAt desc
            """)
    List<DailyPlanItemQueryRow> findRows(@Param("userId") Long userId,
                                         @Param("fromDate") LocalDate fromDate,
                                         @Param("toDate") LocalDate toDate);

    @Query("""
            select count(distinct item.taskId) from DailyPlanItemEntity item
            where item.userId = :userId
              and item.planDate = :planDate
              and item.taskId in :taskIds
            """)
    long countDistinctTaskIds(@Param("userId") Long userId,
                              @Param("planDate") LocalDate planDate,
                              @Param("taskIds") Set<Long> taskIds);

    Optional<DailyPlanItemEntity> findByIdAndUserId(Long id, Long userId);

    Optional<DailyPlanItemEntity> findByIdAndUserIdAndPlanDate(
            Long itemId,
            Long userId,
            LocalDate planDate
    );
}
