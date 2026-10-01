package org.nors.dev.codes.lpu.service;

import java.time.LocalDate;
import java.util.List;
import org.nors.dev.codes.lpu.dto.ErpEmployeeAttendanceLogResponse;
import org.nors.dev.codes.lpu.dto.ErpEmployeeAttendanceResponse;
import org.nors.dev.codes.lpu.model.AttendanceLog;
import org.nors.dev.codes.lpu.model.KioskGroup;
import org.nors.dev.codes.lpu.repository.AttendanceLogRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ErpAttendanceService {

    private static final int DEFAULT_LIMIT = 1_000;
    private static final int MAX_LIMIT = 5_000;

    private final AttendanceLogRepository attendanceLogRepository;

    public ErpAttendanceService(AttendanceLogRepository attendanceLogRepository) {
        this.attendanceLogRepository = attendanceLogRepository;
    }

    /**
     * Main-gate employee daily logs for an ERP pull.
     * Each row is one employee on one campus day: first time in and latest time out.
     */
    @Transactional(readOnly = true)
    public ErpEmployeeAttendanceResponse employeeMainGateLogs(
            LocalDate startDate,
            LocalDate endDate,
            String employeeNo,
            Integer offset,
            Integer limit
    ) {
        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate and endDate are required");
        }
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must be on or before endDate");
        }
        if (startDate.plusDays(366).isBefore(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date range cannot exceed 366 days");
        }

        int pageOffset = offset == null ? 0 : offset;
        if (pageOffset < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "offset must be zero or greater");
        }
        int pageLimit = limit == null ? DEFAULT_LIMIT : limit;
        if (pageLimit < 1 || pageLimit > MAX_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 5000");
        }

        String employeeNumber = blankToNull(employeeNo);
        List<AttendanceLog> logs = attendanceLogRepository.findEmployeeLogs(
                startDate, endDate, KioskGroup.MAIN_GATES, employeeNumber, pageOffset, pageLimit
        );
        long total = attendanceLogRepository.countEmployeeLogs(
                startDate, endDate, KioskGroup.MAIN_GATES, employeeNumber
        );
        return new ErpEmployeeAttendanceResponse(
                startDate,
                endDate,
                total,
                pageOffset,
                pageLimit,
                logs.stream().map(ErpEmployeeAttendanceLogResponse::from).toList()
        );
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
