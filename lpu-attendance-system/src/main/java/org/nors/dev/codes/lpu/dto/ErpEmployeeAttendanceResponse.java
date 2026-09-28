package org.nors.dev.codes.lpu.dto;

import java.time.LocalDate;
import java.util.List;

public record ErpEmployeeAttendanceResponse(
        LocalDate startDate,
        LocalDate endDate,
        long total,
        int offset,
        int limit,
        List<ErpEmployeeAttendanceLogResponse> records
) {
}
