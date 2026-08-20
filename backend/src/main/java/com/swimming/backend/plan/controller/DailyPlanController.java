package com.swimming.backend.plan.controller;

import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.plan.dto.DailyPlanResponse;
import com.swimming.backend.plan.dto.UpdateDailyPlanRequest;
import com.swimming.backend.plan.usecase.DailyPlanUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/daily-plan")
@RequiredArgsConstructor
public class DailyPlanController {

    private final DailyPlanUseCase dailyPlanUseCase;

    @GetMapping
    public ResponseEntity<List<DailyPlanResponse>> getRange(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(name = "from_date")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,
            @RequestParam(name = "to_date")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate
    ) {
        return ResponseEntity.ok(dailyPlanUseCase.getRange(
                authUser.id(),
                fromDate,
                toDate
        ));
    }

    @PutMapping
    public ResponseEntity<Void> update(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody UpdateDailyPlanRequest request
    ) {
        dailyPlanUseCase.update(authUser.id(), request);
        return ResponseEntity.noContent().build();
    }
}
