package org.nors.dev.codes.lpu.dto;

import java.time.Instant;
import java.time.LocalDate;
import org.nors.dev.codes.lpu.model.AttendanceLog;
import org.nors.dev.codes.lpu.model.Employee;
import org.nors.dev.codes.lpu.model.Student;

public record TapResponse(
        String action,
        String message,
        String attendanceId,
        LocalDate attendanceDate,
        Instant timeIn,
        Instant timeOut,
        String location,
        String timeInLocation,
        String timeOutLocation,
        boolean birthday,
        boolean financeTagged,
        String warningMessage,
        String personType,
        StudentResponse student,
        EmployeeResponse employee
) {
    public static TapResponse from(AttendanceLog log, String action, String message) {
        Student student = log.getStudent();
        Employee employee = log.getEmployee();
        String location = "TIME_OUT".equals(action) ? log.getTimeOutLocation() : log.getTimeInLocation();
        LocalDate birthdate = student != null
                ? student.getBirthdate()
                : (employee != null ? employee.getBirthdate() : null);
        LocalDate date = log.getAttendanceDate();
        boolean birthday = birthdate != null && date != null
                && birthdate.getMonthValue() == date.getMonthValue()
                && birthdate.getDayOfMonth() == date.getDayOfMonth();
        boolean financeTagged = student != null && student.isFinanceTagged();
        return new TapResponse(
                action,
                message,
                String.valueOf(log.getId()),
                log.getAttendanceDate(),
                log.getTimeIn(),
                log.getTimeOut(),
                location,
                log.getTimeInLocation(),
                log.getTimeOutLocation(),
                birthday,
                financeTagged,
                financeTagged ? "PLEASE VISIT FINANCE DEPARTMENT" : null,
                student != null ? "STUDENT" : "EMPLOYEE",
                student != null ? StudentResponse.from(student) : null,
                employee != null ? EmployeeResponse.from(employee) : null
        );
    }
}
