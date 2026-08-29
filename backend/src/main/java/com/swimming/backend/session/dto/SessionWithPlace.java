package com.swimming.backend.session.dto;

import com.swimming.backend.place.dto.projection.PlaceReferenceRow;
import com.swimming.backend.session.domain.Session;

public record SessionWithPlace(
        Session session,
        PlaceReferenceRow place
) {
}
