package org.nors.dev.codes.lpu.dto;

import java.util.List;

public record AttendancePageResponse(
        List<AttendanceDailyResponse> items,
        long total
) {
}
