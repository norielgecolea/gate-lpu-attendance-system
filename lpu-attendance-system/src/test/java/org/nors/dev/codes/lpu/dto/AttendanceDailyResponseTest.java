package org.nors.dev.codes.lpu.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.nors.dev.codes.lpu.model.AttendanceLog;
import org.nors.dev.codes.lpu.model.Employee;
import org.nors.dev.codes.lpu.model.Student;

class AttendanceDailyResponseTest {

    @Test
    void mapsOpenStudentDay() {
        Student student = new Student();
        student.setId(11L);
        student.setName("AALA, ALIYAH");
        student.setStudentNo("2024-10899");
        student.setDepartment("CITHM");
        student.setCourse("BSIHM");
        student.setSchool("LPL");

        AttendanceLog log = new AttendanceLog();
        log.setId(99L);
        log.setStudent(student);
        log.setAttendanceDate(LocalDate.of(2026, 7, 17));
        log.setTimeIn(Instant.parse("2026-07-17T00:00:00Z"));
        log.setLastAction("TIME_IN");
        log.setTapCount(3);

        AttendanceDailyResponse response = AttendanceDailyResponse.from(log);

        assertEquals("99", response.id());
        assertEquals("STUDENT", response.personType());
        assertEquals("11", response.personId());
        assertEquals("2024-10899", response.personNo());
        assertEquals("OPEN", response.status());
        assertEquals(3, response.tapCount());
        assertEquals("BSIHM", response.course());
        assertNull(response.position());
    }

    @Test
    void mapsCompleteEmployeeDay() {
        Employee employee = new Employee();
        employee.setId(7L);
        employee.setName("Santos, Ana");
        employee.setEmployeeNo("EMP-001");
        employee.setDepartment("Admin");
        employee.setPosition("Clerk");

        AttendanceLog log = new AttendanceLog();
        log.setId(5L);
        log.setEmployee(employee);
        log.setAttendanceDate(LocalDate.of(2026, 7, 17));
        log.setTimeIn(Instant.parse("2026-07-17T00:00:00Z"));
        log.setTimeOut(Instant.parse("2026-07-17T08:00:00Z"));
        log.setLastAction("TIME_OUT");
        log.setTapCount(2);

        AttendanceDailyResponse response = AttendanceDailyResponse.from(log);

        assertEquals("EMPLOYEE", response.personType());
        assertEquals("COMPLETE", response.status());
        assertEquals("Clerk", response.position());
        assertNull(response.course());
        assertEquals("EMP-001", response.personNo());
    }
}
