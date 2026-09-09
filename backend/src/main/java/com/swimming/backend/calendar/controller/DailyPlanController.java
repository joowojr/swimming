package com.swimming.backend.calendar.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.calendar.dto.CreateDailyPlanItemsRequest;
import com.swimming.backend.calendar.dto.DailyPlanResponse;
import com.swimming.backend.calendar.dto.ReorderDailyPlanItemsRequest;
import com.swimming.backend.calendar.usecase.DailyPlanUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "데일리 플랜", description = "핀보드(`/pinboard`)의 데일리 플래너.")
@RestController
@RequestMapping("/api/daily-plans")
@RequiredArgsConstructor
public class DailyPlanController {

    private final DailyPlanUseCase dailyPlanUseCase;

    @GetMapping
    public ResponseEntity<List<DailyPlanResponse>> getRange(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(name = "from_date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(name = "to_date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {
        return ResponseEntity.ok(dailyPlanUseCase.getRange(authUser.id(), fromDate, toDate));
    }

    @PutMapping("/{date}")
    public ResponseEntity<DailyPlanResponse> reorder(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody ReorderDailyPlanItemsRequest request
    ) {
        return ResponseEntity.ok(dailyPlanUseCase.reorder(authUser.id(), date, request));
    }

    @PostMapping("/{date}/items")
    public ResponseEntity<DailyPlanResponse> addItems(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody CreateDailyPlanItemsRequest request
    ) {
        DailyPlanResponse response = dailyPlanUseCase.addItems(authUser.id(), date, request);
        return ResponseEntity.created(URI.create("/api/daily-plans/" + date)).body(response);
    }

    @DeleteMapping("/{date}/items/{itemId}")
    public ResponseEntity<Void> deleteItem(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable Long itemId
    ) {
        dailyPlanUseCase.deleteItem(authUser.id(), date, itemId);
        return ResponseEntity.noContent().build();
    }
}
