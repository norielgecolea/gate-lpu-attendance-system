package org.nors.dev.codes.lpu.dto;

import java.time.Instant;
import java.time.LocalDate;
import org.nors.dev.codes.lpu.model.AttendanceLog;
import org.nors.dev.codes.lpu.model.Employee;
import org.nors.dev.codes.lpu.model.Student;

public record AttendanceDailyResponse(
        String id,
        String personType,
        String personId,
        String name,
        String personNo,
        String photo,
        String department,
        String course,
        String school,
        String position,
        LocalDate attendanceDate,
        Instant timeIn,
        Instant timeOut,
        String timeInLocation,
        String timeOutLocation,
        int tapCount,
        String status,
        String lastAction
) {
    public static AttendanceDailyResponse from(AttendanceLog log) {
        Student student = log.getStudent();
        Employee employee = log.getEmployee();
        boolean isStudent = student != null;
        String status = log.getTimeOut() == null ? "OPEN" : "COMPLETE";
        return new AttendanceDailyResponse(
                String.valueOf(log.getId()),
                isStudent ? "STUDENT" : "EMPLOYEE",
                String.valueOf(isStudent ? student.getId() : employee.getId()),
                isStudent ? student.getName() : employee.getName(),
                isStudent ? student.getStudentNo() : employee.getEmployeeNo(),
                isStudent ? student.getPhoto() : employee.getPhoto(),
                isStudent ? student.getDepartment() : employee.getDepartment(),
                isStudent ? student.getCourse() : null,
                isStudent ? student.getSchool() : null,
                isStudent ? null : employee.getPosition(),
                log.getAttendanceDate(),
                log.getTimeIn(),
                log.getTimeOut(),
                log.getTimeInLocation(),
                log.getTimeOutLocation(),
                Math.max(log.getTapCount(), 1),
                status,
                log.getLastAction()
        );
    }
}
