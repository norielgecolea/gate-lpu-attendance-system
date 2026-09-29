package org.nors.dev.codes.lpu.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.nors.dev.codes.lpu.dto.ErpEmployeeAttendanceResponse;
import org.nors.dev.codes.lpu.model.AttendanceLog;
import org.nors.dev.codes.lpu.model.Employee;
import org.nors.dev.codes.lpu.model.KioskGroup;
import org.nors.dev.codes.lpu.repository.AttendanceLogRepository;
import org.springframework.web.server.ResponseStatusException;

class ErpAttendanceServiceTest {

    @Test
    void employeeMainGateLogs_returnsNameNumberAndTimes() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 28);
        AttendanceLog log = log(start, Instant.parse("2026-09-01T00:05:00Z"), Instant.parse("2026-09-01T09:10:00Z"));
        RecordingRepository repository = new RecordingRepository(List.of(log), 1);
        ErpAttendanceService service = new ErpAttendanceService(repository);

        ErpEmployeeAttendanceResponse response = service.employeeMainGateLogs(start, end, null, null);

        assertEquals(start, response.startDate());
        assertEquals(end, response.endDate());
        assertEquals(1, response.total());
        assertEquals("Maria Santos", response.records().getFirst().name());
        assertEquals("EMP-1001", response.records().getFirst().employeeNo());
        assertEquals(start, response.records().getFirst().attendanceDate());
        assertEquals(OffsetDateTime.parse("2026-09-01T08:05:00+08:00"), response.records().getFirst().timeIn());
        assertEquals(OffsetDateTime.parse("2026-09-01T17:10:00+08:00"), response.records().getFirst().timeOut());
        assertEquals(KioskGroup.MAIN_GATES, repository.kioskGroup);
        assertEquals(0, repository.offset);
        assertEquals(1000, repository.limit);
    }

    @Test
    void employeeMainGateLogs_keepsOpenDaysWithoutTimeOut() {
        LocalDate day = LocalDate.of(2026, 9, 2);
        AttendanceLog log = log(day, Instant.parse("2026-09-01T23:58:00Z"), null);
        ErpAttendanceService service = new ErpAttendanceService(new RecordingRepository(List.of(log), 70));

        ErpEmployeeAttendanceResponse response = service.employeeMainGateLogs(day, day, 20, 50);

        assertNull(response.records().getFirst().timeOut());
        assertEquals(70, response.total());
        assertEquals(20, response.offset());
        assertEquals(50, response.limit());
    }

    @Test
    void employeeMainGateLogs_requiresBothDates() {
        ErpAttendanceService service = new ErpAttendanceService(new RecordingRepository(List.of(), 0));
        assertThrows(
                ResponseStatusException.class,
                () -> service.employeeMainGateLogs(LocalDate.of(2026, 9, 1), null, null, null)
        );
    }

    @Test
    void employeeMainGateLogs_rejectsInvertedRange() {
        ErpAttendanceService service = new ErpAttendanceService(new RecordingRepository(List.of(), 0));
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.employeeMainGateLogs(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 1), null, null)
        );
        assertEquals("startDate must be on or before endDate", ex.getReason());
    }

    private static AttendanceLog log(LocalDate date, Instant timeIn, Instant timeOut) {
        Employee employee = new Employee();
        employee.setName("Maria Santos");
        employee.setEmployeeNo("EMP-1001");
        AttendanceLog log = new AttendanceLog();
        log.setEmployee(employee);
        log.setAttendanceDate(date);
        log.setTimeIn(timeIn);
        log.setTimeOut(timeOut);
        log.setKioskGroup(KioskGroup.MAIN_GATES);
        return log;
    }

    private static final class RecordingRepository extends AttendanceLogRepository {
        private final List<AttendanceLog> logs;
        private final long total;
        private KioskGroup kioskGroup;
        private int offset;
        private int limit;

        private RecordingRepository(List<AttendanceLog> logs, long total) {
            super(null);
            this.logs = logs;
            this.total = total;
        }

        @Override
        public List<AttendanceLog> findEmployeeLogs(
                LocalDate startDate,
                LocalDate endDate,
                KioskGroup kioskGroup,
                int offset,
                int limit
        ) {
            this.kioskGroup = kioskGroup;
            this.offset = offset;
            this.limit = limit;
            return logs;
        }

        @Override
        public long countEmployeeLogs(LocalDate startDate, LocalDate endDate, KioskGroup kioskGroup) {
            return total;
        }
    }
}
