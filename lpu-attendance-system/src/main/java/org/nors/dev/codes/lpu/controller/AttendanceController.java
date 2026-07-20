package org.nors.dev.codes.lpu.controller;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.nors.dev.codes.lpu.dto.AttendanceDepartmentCountResponse;
import org.nors.dev.codes.lpu.dto.AttendanceEventPageResponse;
import org.nors.dev.codes.lpu.dto.AttendanceHourCountResponse;
import org.nors.dev.codes.lpu.dto.AttendancePageResponse;
import org.nors.dev.codes.lpu.dto.AttendanceSummaryResponse;
import org.nors.dev.codes.lpu.dto.TapRequest;
import org.nors.dev.codes.lpu.dto.TapResponse;
import org.nors.dev.codes.lpu.security.AuthenticatedUser;
import org.nors.dev.codes.lpu.service.AttendanceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @PostMapping("/tap")
    public ResponseEntity<TapResponse> tap(
            @Valid @RequestBody TapRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        Long userId = user != null ? user.getId() : null;
        String location = user != null ? user.getLocation() : null;
        return ResponseEntity.ok(attendanceService.tap(request.identifier(), userId, location));
    }

    @GetMapping("/recent")
    public ResponseEntity<List<TapResponse>> recent(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        return ResponseEntity.ok(attendanceService.recent(limit, offset));
    }

    @GetMapping
    public ResponseEntity<AttendancePageResponse> page(
            @RequestParam String personType,
            @RequestParam(required = false) Long personId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "date") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(attendanceService.pageDaily(
                personType, personId, startDate, endDate, search, department, location, status,
                sortBy, sortDir, offset, limit
        ));
    }

    @GetMapping("/summary")
    public ResponseEntity<AttendanceSummaryResponse> summary(
            @RequestParam String personType,
            @RequestParam(required = false) Long personId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(attendanceService.summary(
                personType, personId, startDate, endDate, search, department, location, status
        ));
    }

    @GetMapping("/by-hour")
    public ResponseEntity<List<AttendanceHourCountResponse>> byHour() {
        return ResponseEntity.ok(attendanceService.byHourToday());
    }

    @GetMapping("/by-department")
    public ResponseEntity<List<AttendanceDepartmentCountResponse>> byDepartment(
            @RequestParam String personType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(attendanceService.byDepartment(personType, startDate, endDate));
    }

    @GetMapping("/events")
    public ResponseEntity<AttendanceEventPageResponse> events(
            @RequestParam String personType,
            @RequestParam(required = false) Long personId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(attendanceService.pageEvents(
                personType, personId, startDate, endDate, search, location, action, sortDir, offset, limit
        ));
    }

    @GetMapping("/locations")
    public ResponseEntity<List<String>> locations(
            @RequestParam String personType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(attendanceService.locations(personType, startDate, endDate));
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(
            @RequestParam String personType,
            @RequestParam(required = false) Long personId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String status
    ) {
        byte[] csv = attendanceService.exportCsv(
                personType, personId, startDate, endDate, search, department, location, status
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"attendance.csv\"")
                .contentType(new MediaType("text", "csv"))
                .body(csv);
    }
}
