package org.nors.dev.codes.lpu.dto;

public record PasswordChangeResponse(
        String message,
        String token,
        String tokenType,
        long expiresInMs
) {
}
