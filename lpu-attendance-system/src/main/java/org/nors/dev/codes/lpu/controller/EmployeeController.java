package org.nors.dev.codes.lpu.controller;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.nors.dev.codes.lpu.dto.AttendanceEventPageResponse;
import org.nors.dev.codes.lpu.dto.AttendancePageResponse;
import org.nors.dev.codes.lpu.dto.EmployeeImportResponse;
import org.nors.dev.codes.lpu.dto.EmployeeRequest;
import org.nors.dev.codes.lpu.dto.EmployeeResponse;
import org.nors.dev.codes.lpu.dto.PersonAttendanceSummaryResponse;
import org.nors.dev.codes.lpu.dto.PhotoUploadResponse;
import org.nors.dev.codes.lpu.service.AttendanceService;
import org.nors.dev.codes.lpu.service.EmployeeService;
import org.nors.dev.codes.lpu.service.PhotoStorageService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final PhotoStorageService photoStorageService;
    private final AttendanceService attendanceService;

    public EmployeeController(
            EmployeeService employeeService,
            PhotoStorageService photoStorageService,
            AttendanceService attendanceService
    ) {
        this.employeeService = employeeService;
        this.photoStorageService = photoStorageService;
        this.attendanceService = attendanceService;
    }

    @GetMapping
    public ResponseEntity<List<EmployeeResponse>> list() {
        return ResponseEntity.ok(employeeService.listActive());
    }

    @GetMapping("/inactive")
    public ResponseEntity<List<EmployeeResponse>> listInactive() {
        return ResponseEntity.ok(employeeService.listInactive());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmployeeResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.getById(id));
    }

    @GetMapping("/{id}/attendance")
    public ResponseEntity<AttendancePageResponse> attendance(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "date") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(attendanceService.pageDaily(
                "EMPLOYEE", id, startDate, endDate, null, null, null, status, sortBy, sortDir, offset, limit
        ));
    }

    @GetMapping("/{id}/attendance/summary")
    public ResponseEntity<PersonAttendanceSummaryResponse> attendanceSummary(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(attendanceService.personSummary("EMPLOYEE", id, startDate, endDate));
    }

    @GetMapping("/{id}/attendance/events")
    public ResponseEntity<AttendanceEventPageResponse> attendanceEvents(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(attendanceService.pageEvents(
                "EMPLOYEE", id, startDate, endDate, null, location, action, sortDir, offset, limit
        ));
    }

    @GetMapping(value = "/{id}/attendance/export", produces = "text/csv")
    public ResponseEntity<byte[]> exportAttendance(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String action
    ) {
        byte[] csv = attendanceService.exportPersonEventsCsv("EMPLOYEE", id, startDate, endDate, location, action);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"employee-attendance.csv\"")
                .contentType(new MediaType("text", "csv"))
                .body(csv);
    }

    @PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PhotoUploadResponse> uploadPhoto(@RequestPart("file") MultipartFile file) {
        String photoPath = photoStorageService.store(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(new PhotoUploadResponse(photoPath));
    }

    @PostMapping
    public ResponseEntity<EmployeeResponse> create(@Valid @RequestBody EmployeeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeeService.create(request));
    }

    @PostMapping("/import")
    public ResponseEntity<EmployeeImportResponse> importEmployees(
            @RequestBody List<@Valid EmployeeRequest> requests
    ) {
        return ResponseEntity.ok(employeeService.importEmployees(requests));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EmployeeResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody EmployeeRequest request
    ) {
        return ResponseEntity.ok(employeeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        employeeService.delete(id);
        return ResponseEntity.ok(Map.of("message", "Employee deactivated"));
    }

    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Map<String, String>> permanentlyDelete(@PathVariable Long id) {
        attendanceService.permanentlyDeleteInactivePerson("EMPLOYEE", id);
        return ResponseEntity.ok(Map.of("message", "Employee and attendance history permanently deleted"));
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<EmployeeResponse> restore(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.restore(id));
    }
}
