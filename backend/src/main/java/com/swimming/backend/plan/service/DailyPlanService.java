package com.swimming.backend.plan.service;

import com.swimming.backend.plan.domain.DailyPlan;
import com.swimming.backend.plan.repository.DailyPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DailyPlanService {

    private final DailyPlanRepository dailyPlanRepository;

    public Optional<DailyPlan> get(Long userId, LocalDate date) {
        return dailyPlanRepository.findByUserIdAndPlanDate(userId, date);
    }

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
                );
    }

    public DailyPlan create(Long userId, LocalDate date, List<Long> taskIds) {
        return dailyPlanRepository.save(DailyPlan.create(userId, date, taskIds));
    }

    public DailyPlan update(DailyPlan dailyPlan, List<Long> taskIds) {
        dailyPlan.replaceItems(taskIds);
        return dailyPlan;
    }

    public boolean containsTask(Long userId, LocalDate date, Long taskId) {
        return dailyPlanRepository.containsTask(userId, date, taskId);
    }
}
