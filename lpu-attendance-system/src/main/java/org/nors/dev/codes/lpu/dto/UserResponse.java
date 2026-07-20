package org.nors.dev.codes.lpu.dto;

import java.time.Instant;
import org.nors.dev.codes.lpu.model.User;

public record UserResponse(
        String id,
        String username,
        String role,
        String location,
        boolean active,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                String.valueOf(user.getId()),
                user.getUsername(),
                user.getRole().name(),
                user.getLocation(),
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
