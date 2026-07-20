package org.nors.dev.codes.lpu.dto;

import java.time.Instant;

public record AuthEventMessage(
        String type,
        String username,
        String message,
        Instant timestamp
) {
    public static AuthEventMessage of(String type, String username, String message) {
        return new AuthEventMessage(type, username, message, Instant.now());
    }
}
