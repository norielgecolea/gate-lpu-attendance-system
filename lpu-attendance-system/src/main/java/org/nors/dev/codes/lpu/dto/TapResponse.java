package org.nors.dev.codes.lpu.dto;

import java.time.Instant;
import java.time.LocalDate;
import org.nors.dev.codes.lpu.model.AttendanceEvent;
import org.nors.dev.codes.lpu.model.AttendanceLog;
import org.nors.dev.codes.lpu.model.Employee;
import org.nors.dev.codes.lpu.model.KioskGroup;
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
        boolean alarmMarked,
        String warningMessage,
        String personType,
        String kioskGroup,
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
        boolean alarmMarked = (student != null && student.isAlarmMarked())
                || (employee != null && employee.isAlarmMarked());
        String kioskGroup = log.getKioskGroup() != null ? log.getKioskGroup().name() : KioskGroup.MAIN_GATES.name();
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
                alarmMarked,
                financeTagged ? "PLEASE VISIT FINANCE DEPARTMENT" : null,
                student != null ? "STUDENT" : "EMPLOYEE",
                kioskGroup,
                student != null ? StudentResponse.from(student) : null,
                employee != null ? EmployeeResponse.from(employee) : null
        );
    }

    /** One physical tap: its own time and gate, not the day's first in and last out. */
    public static TapResponse from(AttendanceEvent event) {
        Student student = event.getStudent();
        Employee employee = event.getEmployee();
        boolean timeOut = "TIME_OUT".equals(event.getAction());
        String location = event.getLocation();
        LocalDate birthdate = student != null
                ? student.getBirthdate()
                : (employee != null ? employee.getBirthdate() : null);
        LocalDate date = event.getAttendanceDate();
        boolean birthday = birthdate != null && date != null
                && birthdate.getMonthValue() == date.getMonthValue()
                && birthdate.getDayOfMonth() == date.getDayOfMonth();
        boolean financeTagged = student != null && student.isFinanceTagged();
        boolean alarmMarked = (student != null && student.isAlarmMarked())
                || (employee != null && employee.isAlarmMarked());
        String kioskGroup = event.getKioskGroup() != null
                ? event.getKioskGroup().name()
                : KioskGroup.MAIN_GATES.name();
        return new TapResponse(
                event.getAction(),
                timeOut ? "Timed out" : "Timed in",
                String.valueOf(event.getId()),
                event.getAttendanceDate(),
                timeOut ? null : event.getTappedAt(),
                timeOut ? event.getTappedAt() : null,
                location,
                timeOut ? null : location,
                timeOut ? location : null,
                birthday,
                financeTagged,
                alarmMarked,
                financeTagged ? "PLEASE VISIT FINANCE DEPARTMENT" : null,
                student != null ? "STUDENT" : "EMPLOYEE",
                kioskGroup,
                student != null ? StudentResponse.from(student) : null,
                employee != null ? EmployeeResponse.from(employee) : null
        );
    }
}
