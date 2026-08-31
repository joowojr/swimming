package com.swimming.backend.session.dto;

import com.swimming.backend.place.domain.Place;
import com.swimming.backend.session.domain.Session;

public record SessionWithPlace(
        Session session,
        Place place
) {
}
