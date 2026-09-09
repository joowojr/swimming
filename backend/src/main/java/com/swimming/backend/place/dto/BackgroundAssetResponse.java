package com.swimming.backend.place.dto;

import com.swimming.backend.place.domain.BackgroundAssetType;

public record BackgroundAssetResponse(
        BackgroundAssetType type,
        String key,
        String url,
        String thumbnailKey,
        String thumbnailUrl
) {
}
