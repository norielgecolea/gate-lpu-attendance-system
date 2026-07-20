package org.nors.dev.codes.lpu.dto;

import java.util.List;

public record AttendanceEventPageResponse(
        List<AttendanceEventResponse> items,
        long total
) {
}
