package com.swimming.backend.knowledge.repository;

import java.util.UUID;

/** Summary embedding 검색 결과. pgvector cosine distance는 작을수록 가깝다. */
public record SimilarSource(UUID sourceId, String title, double distance) {
}
