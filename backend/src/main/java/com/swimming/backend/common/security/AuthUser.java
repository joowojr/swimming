package com.swimming.backend.common.security;

import java.security.Principal;

public record AuthUser(Long id, String email) implements Principal {

    @Override
    public String getName() {
        return id.toString();
    }
}
