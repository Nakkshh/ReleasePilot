package com.releasepilot.dto;

import com.releasepilot.model.AppUser;
import com.releasepilot.model.Role;

import java.time.Instant;

public record UserView(Long id, String username, Role role, boolean enabled, Instant createdAt) {

    public static UserView of(AppUser u) {
        return new UserView(u.getId(), u.getUsername(), u.getRole(), u.isEnabled(), u.getCreatedAt());
    }
}