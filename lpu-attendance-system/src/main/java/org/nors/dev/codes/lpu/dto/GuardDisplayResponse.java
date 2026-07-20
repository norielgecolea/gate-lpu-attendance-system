package org.nors.dev.codes.lpu.dto;

import java.util.List;

public record GuardDisplayResponse(
        String mode,
        List<GuardVideoResponse> videos
) {
}
