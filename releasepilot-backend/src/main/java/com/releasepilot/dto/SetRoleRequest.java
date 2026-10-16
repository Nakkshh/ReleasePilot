package com.releasepilot.dto;

import com.releasepilot.model.Role;
import jakarta.validation.constraints.NotNull;

public record SetRoleRequest(@NotNull(message = "role is required (ADMIN or APPROVER)") Role role) {
}