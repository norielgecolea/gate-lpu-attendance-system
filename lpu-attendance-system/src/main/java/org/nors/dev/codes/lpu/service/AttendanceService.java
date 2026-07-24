package org.nors.dev.codes.lpu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nors.dev.codes.lpu.dto.AttendanceDailyResponse;
import org.nors.dev.codes.lpu.dto.AttendanceDepartmentCountResponse;
import org.nors.dev.codes.lpu.dto.AttendanceEventPageResponse;
import org.nors.dev.codes.lpu.dto.AttendanceEventResponse;
import org.nors.dev.codes.lpu.dto.AttendanceHourCountResponse;
import org.nors.dev.codes.lpu.dto.AttendancePageResponse;
import org.nors.dev.codes.lpu.dto.AttendanceSummaryResponse;
import org.nors.dev.codes.lpu.dto.PersonAttendanceSummaryResponse;
import org.nors.dev.codes.lpu.dto.TapResponse;
import org.nors.dev.codes.lpu.model.AttendanceEvent;
import org.nors.dev.codes.lpu.model.AttendanceLog;
import org.nors.dev.codes.lpu.model.Employee;
import org.nors.dev.codes.lpu.model.Student;
import org.nors.dev.codes.lpu.model.User;
import org.nors.dev.codes.lpu.repository.AttendanceEventRepository;
import org.nors.dev.codes.lpu.repository.AttendanceLogRepository;
import org.nors.dev.codes.lpu.repository.EmployeeRepository;
import org.nors.dev.codes.lpu.repository.StudentRepository;
import org.nors.dev.codes.lpu.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AttendanceService {

    private static final Logger log = LogManager.getLogger(AttendanceService.class);
    private static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Manila");
    private static final String ACTION_IN = "TIME_IN";
    private static final String ACTION_OUT = "TIME_OUT";
    /** Repeat taps inside this window don't toggle state — they just re-show the last tap. */
    private static final java.time.Duration TAP_COOLDOWN = java.time.Duration.ofSeconds(10);
    private static final int MAX_PAGE = 200;
    private static final int MAX_EXPORT = 10_000;
    private static final DateTimeFormatter CSV_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(CAMPUS_ZONE);

    private final StudentRepository studentRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final AttendanceEventRepository attendanceEventRepository;
    private final UserRepository userRepository;
    private final TapErrorLogService tapErrorLogService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public AttendanceService(
            StudentRepository studentRepository,
            EmployeeRepository employeeRepository,
            AttendanceLogRepository attendanceLogRepository,
            AttendanceEventRepository attendanceEventRepository,
            UserRepository userRepository,
            TapErrorLogService tapErrorLogService,
            NotificationService notificationService,
            ObjectMapper objectMapper
    ) {
        this.studentRepository = studentRepository;
        this.employeeRepository = employeeRepository;
        this.attendanceLogRepository = attendanceLogRepository;
        this.attendanceEventRepository = attendanceEventRepository;
        this.userRepository = userRepository;
        this.tapErrorLogService = tapErrorLogService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    /**
     * Alternating tap cycles are allowed all day.
     * Final daily record always keeps the first {@code time_in} and the latest {@code time_out}.
     * Every accepted tap is also stored as an immutable attendance event.
     */
    @Transactional
    public TapResponse tap(String rawIdentifier, Long tappedByUserId, String location) {
        String identifier = rawIdentifier == null ? "" : rawIdentifier.trim();
        if (identifier.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID or RFID is required");
        }

        Student student = studentRepository.findByRfidOrStudentNo(identifier).orElse(null);
        Employee employee = student == null
                ? employeeRepository.findByRfidOrEmployeeNo(identifier).orElse(null)
                : null;
        if (student == null && employee == null) {
            broadcastTapError(identifier, blankToNull(location));
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record Not Found");
        }
        String personRef = student != null
                ? "studentNo=" + student.getStudentNo()
                : "employeeNo=" + employee.getEmployeeNo();

        LocalDate today = LocalDate.now(CAMPUS_ZONE);
        Instant now = Instant.now();
        String gate = blankToNull(location);

        AttendanceLog existing = student != null
                ? attendanceLogRepository.findByStudentAndDateForUpdate(student.getId(), today).orElse(null)
                : attendanceLogRepository.findByEmployeeAndDateForUpdate(employee.getId(), today).orElse(null);
        TapResponse response;

        if (existing != null
                && existing.getUpdatedAt() != null
                && now.isBefore(existing.getUpdatedAt().plus(TAP_COOLDOWN))) {
            String action = existing.getLastAction() != null ? existing.getLastAction() : ACTION_IN;
            String message = ACTION_OUT.equals(action) ? "Time out recorded" : "Time in recorded";
            log.info("TAP cooldown {} lastAction={}", personRef, action);
            return TapResponse.from(existing, action, message);
        }

        if (existing == null) {
            AttendanceLog created = new AttendanceLog();
            created.setStudent(student);
            created.setEmployee(employee);
            created.setAttendanceDate(today);
            created.setTimeIn(now);
            created.setTimeOut(null);
            created.setLastAction(ACTION_IN);
            created.setTappedByUserId(tappedByUserId);
            created.setTimeInLocation(gate);
            created.setTapCount(1);
            created.setCreatedAt(now);
            created.setUpdatedAt(now);
            attendanceLogRepository.persist(created);
            persistEvent(student, employee, today, ACTION_IN, now, gate, tappedByUserId);
            response = TapResponse.from(created, ACTION_IN, "Time in recorded");
            log.info("TIME_IN (first) {} location={}", personRef, gate);
        } else if (ACTION_OUT.equals(existing.getLastAction())) {
            existing.setLastAction(ACTION_IN);
            existing.setTappedByUserId(tappedByUserId);
            existing.setTapCount(existing.getTapCount() + 1);
            existing.setUpdatedAt(now);
            attendanceLogRepository.save(existing);
            persistEvent(student, employee, today, ACTION_IN, now, gate, tappedByUserId);
            response = TapResponse.from(existing, ACTION_IN, "Time in recorded");
            log.info("TIME_IN (again) {} firstIn={} location={}",
                    personRef, existing.getTimeIn(), existing.getTimeInLocation());
        } else {
            existing.setTimeOut(now);
            existing.setLastAction(ACTION_OUT);
            existing.setTappedByUserId(tappedByUserId);
            existing.setTimeOutLocation(gate);
            existing.setTapCount(existing.getTapCount() + 1);
            existing.setUpdatedAt(now);
            attendanceLogRepository.save(existing);
            persistEvent(student, employee, today, ACTION_OUT, now, gate, tappedByUserId);
            response = TapResponse.from(existing, ACTION_OUT, "Time out recorded");
            log.info("TIME_OUT {} firstIn={} lastOut={} location={}",
                    personRef, existing.getTimeIn(), existing.getTimeOut(), gate);
        }

        broadcastTap(response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<TapResponse> recent(int limit, int offset) {
        int size = Math.min(Math.max(limit, 1), 50);
        int from = Math.max(offset, 0);
        LocalDate today = LocalDate.now(CAMPUS_ZONE);
        return attendanceLogRepository.findRecentByDate(today, from, size).stream()
                .map(logEntry -> {
                    String action = logEntry.getLastAction() != null
                            ? logEntry.getLastAction()
                            : (logEntry.getTimeOut() == null ? ACTION_IN : ACTION_OUT);
                    String message = ACTION_OUT.equals(action) ? "Timed out" : "Timed in";
                    return TapResponse.from(logEntry, action, message);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public AttendancePageResponse pageDaily(
            String personType,
            Long personId,
            LocalDate startDate,
            LocalDate endDate,
            String search,
            String department,
            String location,
            String status,
            String sortBy,
            String sortDir,
            int offset,
            int limit
    ) {
        requirePersonType(personType);
        DateRange range = normalizeRange(startDate, endDate);
        int size = Math.min(Math.max(limit, 1), MAX_PAGE);
        int from = Math.max(offset, 0);
        List<AttendanceDailyResponse> items = attendanceLogRepository
                .searchDaily(
                        personType, personId, range.start(), range.end(), search, department, location, status,
                        sortBy, sortDir, from, size
                )
                .stream()
                .map(AttendanceDailyResponse::from)
                .toList();
        long total = attendanceLogRepository.countDaily(
                personType, personId, range.start(), range.end(), search, department, location, status
        );
        return new AttendancePageResponse(items, total);
    }

    @Transactional(readOnly = true)
    public AttendanceSummaryResponse summary(
            String personType,
            Long personId,
            LocalDate startDate,
            LocalDate endDate,
            String search,
            String department,
            String location,
            String status
    ) {
        requirePersonType(personType);
        DateRange range = normalizeRange(startDate, endDate);
        Object[] row = attendanceLogRepository.summarizeDaily(
                personType, personId, range.start(), range.end(), search, department, location, status
        );
        return new AttendanceSummaryResponse(
                toLong(row[0]),
                toLong(row[1]),
                toLong(row[2]),
                toLong(row[3]),
                toLong(row[4])
        );
    }

    /**
     * Permanently removes an inactive person and all attendance data that references them.
     * Events are deleted before daily logs and the person so foreign-key integrity is preserved.
     */
    @Transactional
    public void permanentlyDeleteInactivePerson(String personType, Long personId) {
        requirePersonType(personType);
        if ("STUDENT".equalsIgnoreCase(personType)) {
            Student student = studentRepository.findById(personId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"));
            if (!student.isDeleted()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Deactivate the student before permanently deleting the record"
                );
            }
            int events = attendanceEventRepository.deleteByPerson("STUDENT", personId);
            int logs = attendanceLogRepository.deleteByPerson("STUDENT", personId);
            studentRepository.delete(student);
            log.info("Permanently deleted student id={} attendanceEvents={} attendanceLogs={}",
                    personId, events, logs);
            return;
        }

        Employee employee = employeeRepository.findById(personId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
        if (!employee.isDeleted()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Deactivate the employee before permanently deleting the record"
            );
        }
        int events = attendanceEventRepository.deleteByPerson("EMPLOYEE", personId);
        int logs = attendanceLogRepository.deleteByPerson("EMPLOYEE", personId);
        employeeRepository.delete(employee);
        log.info("Permanently deleted employee id={} attendanceEvents={} attendanceLogs={}",
                personId, events, logs);
    }

    /** Tap volume per hour (campus timezone) for a given day — defaults to today. */
    @Transactional(readOnly = true)
    public List<AttendanceHourCountResponse> byHour(LocalDate date) {
        LocalDate day = date != null ? date : LocalDate.now(CAMPUS_ZONE);
        Map<Integer, long[]> byHour = new HashMap<>();
        for (Object[] row : attendanceEventRepository.countByHour(day)) {
            int hour = ((Number) row[0]).intValue();
            long count = ((Number) row[2]).longValue();
            long[] slot = byHour.computeIfAbsent(hour, h -> new long[2]);
            if (ACTION_OUT.equals(row[1])) {
                slot[1] += count;
            } else {
                slot[0] += count;
            }
        }
        return byHour.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new AttendanceHourCountResponse(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AttendanceDepartmentCountResponse> byDepartment(
            String personType,
            LocalDate startDate,
            LocalDate endDate
    ) {
        requirePersonType(personType);
        DateRange range = normalizeRange(startDate, endDate);
        return attendanceLogRepository.countByDepartment(personType, range.start(), range.end()).stream()
                .map(row -> new AttendanceDepartmentCountResponse(
                        row[0] != null ? row[0].toString() : "Unassigned",
                        toLong(row[1])
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public PersonAttendanceSummaryResponse personSummary(
            String personType,
            Long personId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        requirePersonType(personType);
        requirePerson(personType, personId);
        DateRange range = normalizeRange(startDate, endDate);
        Object[] row = attendanceLogRepository.summarizePerson(personType, personId, range.start(), range.end());
        return new PersonAttendanceSummaryResponse(
                toLong(row[0]),
                toLong(row[1]),
                toLong(row[2]),
                toLong(row[3]),
                (LocalDate) row[4],
                (LocalDate) row[5]
        );
    }

    @Transactional(readOnly = true)
    public AttendanceEventPageResponse pageEvents(
            String personType,
            Long personId,
            LocalDate startDate,
            LocalDate endDate,
            String search,
            String location,
            String action,
            String sortDir,
            int offset,
            int limit
    ) {
        requirePersonType(personType);
        DateRange range = normalizeRange(startDate, endDate);
        int size = Math.min(Math.max(limit, 1), MAX_PAGE);
        int from = Math.max(offset, 0);
        List<AttendanceEvent> events = attendanceEventRepository.search(
                personType, personId, range.start(), range.end(), search, location, action, sortDir, from, size
        );
        Map<Long, String> usernames = usernamesFor(events);
        List<AttendanceEventResponse> items = events.stream()
                .map(event -> AttendanceEventResponse.from(
                        event,
                        event.getTappedByUserId() == null ? null : usernames.get(event.getTappedByUserId())
                ))
                .toList();
        long total = attendanceEventRepository.count(
                personType, personId, range.start(), range.end(), search, location, action
        );
        return new AttendanceEventPageResponse(items, total);
    }

    @Transactional(readOnly = true)
    public List<String> locations(String personType, LocalDate startDate, LocalDate endDate) {
        requirePersonType(personType);
        DateRange range = normalizeRange(startDate, endDate);
        return attendanceLogRepository.distinctLocations(personType, range.start(), range.end());
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(
            String personType,
            Long personId,
            LocalDate startDate,
            LocalDate endDate,
            String search,
            String department,
            String location,
            String status
    ) {
        AttendancePageResponse page = pageDaily(
                personType, personId, startDate, endDate, search, department, location, status,
                "date", "desc", 0, MAX_EXPORT
        );
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
            boolean student = "STUDENT".equalsIgnoreCase(personType);
            if (student) {
                writer.println("Name,Student No,Department,Course,School,Date,Time In,Time Out,Tap Count,Status,Time In Gate,Time Out Gate");
            } else {
                writer.println("Name,Employee No,Department,Position,Date,Time In,Time Out,Tap Count,Status,Time In Gate,Time Out Gate");
            }
            for (AttendanceDailyResponse row : page.items()) {
                if (student) {
                    writer.printf(
                            "%s,%s,%s,%s,%s,%s,%s,%s,%d,%s,%s,%s%n",
                            csv(row.name()),
                            csv(row.personNo()),
                            csv(row.department()),
                            csv(row.course()),
                            csv(row.school()),
                            row.attendanceDate(),
                            csvTime(row.timeIn()),
                            csvTime(row.timeOut()),
                            row.tapCount(),
                            row.status(),
                            csv(row.timeInLocation()),
                            csv(row.timeOutLocation())
                    );
                } else {
                    writer.printf(
                            "%s,%s,%s,%s,%s,%s,%s,%d,%s,%s,%s%n",
                            csv(row.name()),
                            csv(row.personNo()),
                            csv(row.department()),
                            csv(row.position()),
                            row.attendanceDate(),
                            csvTime(row.timeIn()),
                            csvTime(row.timeOut()),
                            row.tapCount(),
                            row.status(),
                            csv(row.timeInLocation()),
                            csv(row.timeOutLocation())
                    );
                }
            }
        }
        return baos.toByteArray();
    }

    @Transactional(readOnly = true)
    public byte[] exportPersonEventsCsv(
            String personType,
            Long personId,
            LocalDate startDate,
            LocalDate endDate,
            String location,
            String action
    ) {
        AttendanceEventPageResponse page = pageEvents(
                personType, personId, startDate, endDate, null, location, action, "desc", 0, MAX_EXPORT
        );
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
            writer.println("Date,Action,Tapped At,Gate,Guard");
            for (AttendanceEventResponse row : page.items()) {
                writer.printf(
                        "%s,%s,%s,%s,%s%n",
                        row.attendanceDate(),
                        row.action(),
                        csvTime(row.tappedAt()),
                        csv(row.location()),
                        csv(row.tappedByUsername())
                );
            }
        }
        return baos.toByteArray();
    }

    private void persistEvent(
            Student student,
            Employee employee,
            LocalDate date,
            String action,
            Instant tappedAt,
            String location,
            Long tappedByUserId
    ) {
        AttendanceEvent event = new AttendanceEvent();
        event.setStudent(student);
        event.setEmployee(employee);
        event.setAttendanceDate(date);
        event.setAction(action);
        event.setTappedAt(tappedAt);
        event.setLocation(location);
        event.setTappedByUserId(tappedByUserId);
        event.setCreatedAt(tappedAt);
        attendanceEventRepository.persist(event);
    }

    private Map<Long, String> usernamesFor(List<AttendanceEvent> events) {
        Set<Long> ids = new HashSet<>();
        for (AttendanceEvent event : events) {
            if (event.getTappedByUserId() != null) {
                ids.add(event.getTappedByUserId());
            }
        }
        Map<Long, String> usernames = new HashMap<>();
        for (Long id : ids) {
            userRepository.findById(id).map(User::getUsername).ifPresent(name -> usernames.put(id, name));
        }
        return usernames;
    }

    private void requirePerson(String personType, Long personId) {
        if (personId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Person id is required");
        }
        if ("EMPLOYEE".equalsIgnoreCase(personType)) {
            employeeRepository.findById(personId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
        } else {
            studentRepository.findById(personId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"));
        }
    }

    private static void requirePersonType(String personType) {
        if (!"STUDENT".equalsIgnoreCase(personType) && !"EMPLOYEE".equalsIgnoreCase(personType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "personType must be STUDENT or EMPLOYEE");
        }
    }

    private static DateRange normalizeRange(LocalDate startDate, LocalDate endDate) {
        LocalDate end = endDate != null ? endDate : LocalDate.now(CAMPUS_ZONE);
        LocalDate start = startDate != null ? startDate : end.minusDays(30);
        if (start.isAfter(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must be on or before endDate");
        }
        if (start.plusDays(366).isBefore(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date range cannot exceed 366 days");
        }
        return new DateRange(start, end);
    }

    private void broadcastTap(TapResponse response) {
        try {
            java.util.Map<String, Object> event = new java.util.LinkedHashMap<>();
            event.put("type", "ATTENDANCE_TAP");
            event.put("action", response.action());
            event.put("message", response.message());
            event.put("payload", response);
            notificationService.broadcastRaw(objectMapper.writeValueAsString(event));
        } catch (Exception ex) {
            log.warn("Failed to broadcast attendance tap", ex);
        }
    }

    /** Persists and broadcasts when a tapped ID/RFID matches no record at a gate. */
    private void broadcastTapError(String identifier, String location) {
        Instant tappedAt = Instant.now();
        try {
            tapErrorLogService.record(identifier, location);
        } catch (Exception ex) {
            log.warn("Failed to persist attendance tap error", ex);
        }
        try {
            java.util.Map<String, Object> event = new java.util.LinkedHashMap<>();
            event.put("type", "ATTENDANCE_TAP_ERROR");
            event.put("message", "Unrecognized ID/RFID tapped");
            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("identifier", identifier);
            payload.put("location", location);
            payload.put("tappedAt", tappedAt.toString());
            event.put("payload", payload);
            notificationService.broadcastRaw(objectMapper.writeValueAsString(event));
        } catch (Exception ex) {
            log.warn("Failed to broadcast attendance tap error", ex);
        }
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private static String csvTime(Instant value) {
        return value == null ? "" : CSV_TIME.format(value);
    }

    private record DateRange(LocalDate start, LocalDate end) {
    }
}
