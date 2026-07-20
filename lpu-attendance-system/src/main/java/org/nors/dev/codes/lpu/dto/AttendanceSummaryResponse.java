package org.nors.dev.codes.lpu.dto;

public record AttendanceSummaryResponse(
        long uniquePeople,
        long completeDays,
        long openDays,
        long totalTaps,
        long currentlyIn
) {
}
