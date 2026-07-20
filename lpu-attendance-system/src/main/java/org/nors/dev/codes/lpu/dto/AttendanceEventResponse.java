package org.nors.dev.codes.lpu.dto;

import java.time.Instant;
import java.time.LocalDate;
import org.nors.dev.codes.lpu.model.AttendanceEvent;
import org.nors.dev.codes.lpu.model.Employee;
import org.nors.dev.codes.lpu.model.Student;

public record AttendanceEventResponse(
        String id,
        String personType,
        String personId,
        String name,
        String personNo,
        LocalDate attendanceDate,
        String action,
        Instant tappedAt,
        String location,
        Long tappedByUserId,
        String tappedByUsername
) {
    public static AttendanceEventResponse from(AttendanceEvent event, String tappedByUsername) {
        Student student = event.getStudent();
        Employee employee = event.getEmployee();
        boolean isStudent = student != null;
        return new AttendanceEventResponse(
                String.valueOf(event.getId()),
                isStudent ? "STUDENT" : "EMPLOYEE",
                String.valueOf(isStudent ? student.getId() : employee.getId()),
                isStudent ? student.getName() : employee.getName(),
                isStudent ? student.getStudentNo() : employee.getEmployeeNo(),
                event.getAttendanceDate(),
                event.getAction(),
                event.getTappedAt(),
                event.getLocation(),
                event.getTappedByUserId(),
                tappedByUsername
        );
    }
}
