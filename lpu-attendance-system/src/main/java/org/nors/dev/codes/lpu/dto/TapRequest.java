package org.nors.dev.codes.lpu.dto;

import jakarta.validation.constraints.NotBlank;

public record TapRequest(
        @NotBlank(message = "ID or RFID is required") String identifier
) {
}
