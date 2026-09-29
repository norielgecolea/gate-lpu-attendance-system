package org.nors.dev.codes.lpu.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.nors.dev.codes.lpu.model.AttendanceLog;
import org.nors.dev.codes.lpu.model.Employee;

public record ErpEmployeeAttendanceLogResponse(
        String name,
        String employeeNo,
        LocalDate attendanceDate,
        OffsetDateTime timeIn,
        OffsetDateTime timeOut
) {
    private static final ZoneId PHILIPPINE_TIME = ZoneId.of("Asia/Manila");

    public static ErpEmployeeAttendanceLogResponse from(AttendanceLog log) {
        Employee employee = log.getEmployee();
        return new ErpEmployeeAttendanceLogResponse(
                employee.getName(),
                employee.getEmployeeNo(),
                log.getAttendanceDate(),
                toPhilippineTime(log.getTimeIn()),
                toPhilippineTime(log.getTimeOut())
        );
    }

    private static OffsetDateTime toPhilippineTime(Instant instant) {
        return instant == null ? null : instant.atZone(PHILIPPINE_TIME).toOffsetDateTime();
    }
}
