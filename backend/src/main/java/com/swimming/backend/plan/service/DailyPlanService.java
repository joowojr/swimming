package com.swimming.backend.plan.service;

import com.swimming.backend.plan.domain.DailyPlan;
import com.swimming.backend.plan.repository.DailyPlanRepository;
import com.swimming.backend.plan.repository.entity.DailyPlanEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DailyPlanService {

    private final DailyPlanRepository dailyPlanRepository;

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public Optional<DailyPlan> get(Long userId, LocalDate date) {
        return dailyPlanRepository.findByUserIdAndPlanDate(userId, date)
                .map(DailyPlanEntity::toDomain);
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<DailyPlan> getRange(
            Long userId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        return dailyPlanRepository
                .findAllByUserIdAndPlanDateBetweenOrderByPlanDateAsc(
                        userId,
                        fromDate,
                        toDate
                )
                .stream()
                .map(DailyPlanEntity::toDomain)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public DailyPlan save(DailyPlan dailyPlan) {
        DailyPlanEntity entity;
        if (dailyPlan.getId() == null) {
            entity = DailyPlanEntity.from(dailyPlan);
        } else {
            entity = dailyPlanRepository
                    .findByUserIdAndPlanDate(dailyPlan.getUserId(), dailyPlan.getPlanDate())
                    .filter(saved -> saved.getId().equals(dailyPlan.getId()))
                    .orElseThrow(() -> new IllegalArgumentException("저장된 데일리 플랜을 찾을 수 없습니다"));
            entity.apply(dailyPlan);
        }
        return dailyPlanRepository.saveAndFlush(entity).toDomain();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public boolean containsTask(Long userId, LocalDate date, Long taskId) {
        return dailyPlanRepository.containsTask(userId, date, taskId);
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public boolean containsAllTasks(Long userId, LocalDate date, List<Long> taskIds) {
        Set<Long> plannedTaskIds = get(userId, date)
                .stream()
                .flatMap(plan -> plan.getItems().stream())
                .map(item -> item.getTaskId())
                .filter(taskId -> taskId != null)
                .collect(Collectors.toSet());
        return plannedTaskIds.containsAll(taskIds);
    }
}
