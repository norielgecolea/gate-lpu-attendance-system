package org.nors.dev.codes.lpu.dto;

public record LoginResponse(
        String token,
        String tokenType,
        String username,
        String role,
        String location,
        long expiresInMs
) {
}
