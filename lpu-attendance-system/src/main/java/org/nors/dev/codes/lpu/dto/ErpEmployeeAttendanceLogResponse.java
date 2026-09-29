package org.nors.dev.codes.lpu.dto;

import java.time.Instant;
import java.time.LocalDate;
import org.nors.dev.codes.lpu.model.AttendanceLog;
import org.nors.dev.codes.lpu.model.Employee;

public record ErpEmployeeAttendanceLogResponse(
        String name,
        String employeeNo,
        LocalDate attendanceDate,
        Instant timeIn,
        Instant timeOut
) {
    public static ErpEmployeeAttendanceLogResponse from(AttendanceLog log) {
        Employee employee = log.getEmployee();
        return new ErpEmployeeAttendanceLogResponse(
                employee.getName(),
                employee.getEmployeeNo(),
                log.getAttendanceDate(),
                log.getTimeIn(),
                log.getTimeOut()
        );
    }
}
