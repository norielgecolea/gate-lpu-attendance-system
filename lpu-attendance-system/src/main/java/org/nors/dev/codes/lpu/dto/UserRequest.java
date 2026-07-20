package org.nors.dev.codes.lpu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserRequest(
        @NotBlank(message = "Username is required")
        @Size(max = 100, message = "Username must be at most 100 characters")
        String username,

        /** Required on create; blank on update keeps the current password. */
        String password,

        @NotBlank(message = "Role is required")
        String role,

        String location
) {
}
