package org.nors.dev.codes.lpu.dto;

public record StudentEnrollmentDeactivateResponse(
        int enrolled,
        int deactivated,
        int alreadyInactive,
        int keptActive,
        int notInSystem
) {
}
