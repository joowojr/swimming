package com.swimming.backend.agentwork.workitem;

import java.util.UUID;

/** Work Item에 연결된 지식 문서. 요약이 아직 없으면 summary는 null이다. */
public record LinkedSource(UUID id, String title, String url, String summary) {
}
