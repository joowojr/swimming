package com.swimming.backend.calendar.dto.in;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 날짜에 할 일을 담는다. 둘 중 하나만 채운다.
 *
 * <ul>
 *   <li>{@code taskIds} — 이미 있는 할 일을 담는다.
 *   <li>{@code tasks} — 새 할 일을 만들어 담는다.
 * </ul>
 *
 * <p>모달의 "모두 추가" 한 번에 여러 건이 오므로 둘 다 목록이다. 한 트랜잭션에서 처리해
 * 일부만 담긴 중간 상태가 생기지 않는다.
 */
public record CreateDailyPlanItemsRequest(
        List<@NotNull Long> taskIds,
        @Size(max = MAX_SIZE, message = "한 번에 담을 수 있는 할 일은 " + MAX_SIZE + "개까지입니다")
        List<@Valid NewDailyPlanTask> tasks
) {
    /** 한 요청이 트랜잭션을 오래 잡지 않게 막는 상한. */
    public static final int MAX_SIZE = 50;

    public static CreateDailyPlanItemsRequest ofTaskIds(List<Long> taskIds) {
        return new CreateDailyPlanItemsRequest(taskIds, null);
    }

    public static CreateDailyPlanItemsRequest ofNewTasks(List<NewDailyPlanTask> tasks) {
        return new CreateDailyPlanItemsRequest(null, tasks);
    }
}
