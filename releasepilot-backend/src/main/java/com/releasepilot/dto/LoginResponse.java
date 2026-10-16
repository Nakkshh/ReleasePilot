package com.releasepilot.dto;

import java.time.Instant;
import java.util.List;

public record LoginResponse(String token, String tokenType, Instant expiresAt,
                            String username, List<String> roles) {
}