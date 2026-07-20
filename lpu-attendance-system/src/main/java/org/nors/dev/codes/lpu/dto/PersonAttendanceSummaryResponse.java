package org.nors.dev.codes.lpu.dto;

import java.time.LocalDate;

public record PersonAttendanceSummaryResponse(
        long daysPresent,
        long completeDays,
        long openDays,
        long totalTaps,
        LocalDate firstDate,
        LocalDate latestDate
) {
}
