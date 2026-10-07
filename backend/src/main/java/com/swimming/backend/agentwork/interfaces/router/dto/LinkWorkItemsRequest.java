package com.swimming.backend.agentwork.interfaces.router.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 진행 중인 세션에 할 일을 붙인다. 다른 세션에 연결된 할 일은 이 세션으로 옮긴다. */
public record LinkWorkItemsRequest(
        @NotEmpty @Size(max = 100) List<@NotNull @Valid AddWorkItemRequest> workItems
) {
}
