package org.nors.dev.codes.lpu.controller;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.nors.dev.codes.lpu.dto.AttendanceEventPageResponse;
import org.nors.dev.codes.lpu.dto.AttendancePageResponse;
import org.nors.dev.codes.lpu.dto.PersonAttendanceSummaryResponse;
import org.nors.dev.codes.lpu.dto.PhotoBulkUploadResponse;
import org.nors.dev.codes.lpu.dto.PhotoUploadResponse;
import org.nors.dev.codes.lpu.dto.StudentAuditEventResponse;
import org.nors.dev.codes.lpu.dto.StudentFinanceTagImportResponse;
import org.nors.dev.codes.lpu.dto.StudentImportResponse;
import org.nors.dev.codes.lpu.dto.StudentImportRequest;
import org.nors.dev.codes.lpu.dto.StudentPageResponse;
import org.nors.dev.codes.lpu.dto.StudentRequest;
import org.nors.dev.codes.lpu.dto.StudentResponse;
import org.nors.dev.codes.lpu.model.KioskGroup;
import org.nors.dev.codes.lpu.model.KioskGroups;
import org.nors.dev.codes.lpu.security.AuthenticatedUser;
import org.nors.dev.codes.lpu.service.AttendanceService;
import org.nors.dev.codes.lpu.service.PhotoStorageService;
import org.nors.dev.codes.lpu.service.StudentService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/students")
public class StudentController {

    private final StudentService studentService;
    private final PhotoStorageService photoStorageService;
    private final AttendanceService attendanceService;

    public StudentController(
            StudentService studentService,
            PhotoStorageService photoStorageService,
            AttendanceService attendanceService
    ) {
        this.studentService = studentService;
        this.photoStorageService = photoStorageService;
        this.attendanceService = attendanceService;
    }

    @GetMapping
    public ResponseEntity<List<StudentResponse>> list() {
        return ResponseEntity.ok(studentService.listActive());
    }

    @GetMapping("/page")
    public ResponseEntity<StudentPageResponse> page(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(studentService.page(search, offset, limit));
    }

    @GetMapping("/inactive")
    public ResponseEntity<List<StudentResponse>> listInactive() {
        return ResponseEntity.ok(studentService.listInactive());
    }

    @GetMapping("/finance-tagged")
    public ResponseEntity<List<StudentResponse>> listFinanceTagged() {
        return ResponseEntity.ok(studentService.listFinanceTagged());
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export() {
        byte[] csv = studentService.exportCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"students.csv\"")
                .contentType(new MediaType("text", "csv"))
                .body(csv);
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudentResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(studentService.getById(id));
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
            @RequestParam(defaultValue = "50") int limit,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ResponseEntity.ok(attendanceService.pageDaily(
                "STUDENT", id, startDate, endDate, null, null, null, status,
                viewGroup(user), sortBy, sortDir, offset, limit
        ));
    }

    @GetMapping("/{id}/attendance/summary")
    public ResponseEntity<PersonAttendanceSummaryResponse> attendanceSummary(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ResponseEntity.ok(attendanceService.personSummary("STUDENT", id, startDate, endDate, viewGroup(user)));
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
            @RequestParam(defaultValue = "50") int limit,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ResponseEntity.ok(attendanceService.pageEvents(
                "STUDENT", id, startDate, endDate, null, location, action, viewGroup(user), sortDir, offset, limit
        ));
    }

    @GetMapping(value = "/{id}/attendance/export", produces = "text/csv")
    public ResponseEntity<byte[]> exportAttendance(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String action,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        byte[] csv = attendanceService.exportPersonEventsCsv(
                "STUDENT", id, startDate, endDate, location, action, viewGroup(user)
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"student-attendance.csv\"")
                .contentType(new MediaType("text", "csv"))
                .body(csv);
    }

    @PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PhotoUploadResponse> uploadPhoto(@RequestPart("file") MultipartFile file) {
        String photoPath = photoStorageService.store(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(new PhotoUploadResponse(photoPath));
    }

    @PostMapping(value = "/photos/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PhotoBulkUploadResponse> bulkUploadPhotos(
            @RequestParam("files") List<MultipartFile> files,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        Long actorUserId = user != null ? user.getId() : null;
        String actorUsername = user != null ? user.getUsername() : null;
        return ResponseEntity.ok(studentService.bulkUploadPhotos(files, actorUserId, actorUsername));
    }

    @PostMapping
    public ResponseEntity<StudentResponse> create(
            @Valid @RequestBody StudentRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        Long actorUserId = user != null ? user.getId() : null;
        String actorUsername = user != null ? user.getUsername() : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(studentService.create(request, actorUserId, actorUsername));
    }

    @GetMapping("/{id}/audit")
    public ResponseEntity<List<StudentAuditEventResponse>> audit(@PathVariable Long id) {
        return ResponseEntity.ok(studentService.listAuditEvents(id));
    }

    @PostMapping("/import")
    public ResponseEntity<StudentImportResponse> importStudents(
            @RequestBody List<StudentImportRequest> requests,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        Long actorUserId = user != null ? user.getId() : null;
        String actorUsername = user != null ? user.getUsername() : null;
        return ResponseEntity.ok(studentService.importStudents(requests, actorUserId, actorUsername));
    }

    @PostMapping("/finance-tagged/import")
    public ResponseEntity<StudentFinanceTagImportResponse> importFinanceTagged(
            @RequestBody List<String> studentNumbers
    ) {
        return ResponseEntity.ok(studentService.importFinanceTags(studentNumbers));
    }

    @PostMapping("/{id}/finance-tagged")
    public ResponseEntity<StudentResponse> financeTag(@PathVariable Long id) {
        return ResponseEntity.ok(studentService.setFinanceTagged(id, true));
    }

    @DeleteMapping("/{id}/finance-tagged")
    public ResponseEntity<StudentResponse> removeFinanceTag(@PathVariable Long id) {
        return ResponseEntity.ok(studentService.setFinanceTagged(id, false));
    }

    @PutMapping("/{id}")
    public ResponseEntity<StudentResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody StudentRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        Long actorUserId = user != null ? user.getId() : null;
        String actorUsername = user != null ? user.getUsername() : null;
        return ResponseEntity.ok(studentService.update(id, request, actorUserId, actorUsername));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        Long actorUserId = user != null ? user.getId() : null;
        String actorUsername = user != null ? user.getUsername() : null;
        studentService.delete(id, actorUserId, actorUsername);
        return ResponseEntity.ok(Map.of("message", "Student deactivated"));
    }

    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Map<String, String>> permanentlyDelete(@PathVariable Long id) {
        attendanceService.permanentlyDeleteInactivePerson("STUDENT", id);
        return ResponseEntity.ok(Map.of("message", "Student and attendance history permanently deleted"));
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<StudentResponse> restore(@PathVariable Long id) {
        return ResponseEntity.ok(studentService.restore(id));
    }

    private static KioskGroup viewGroup(AuthenticatedUser user) {
        return KioskGroups.resolveForView(user != null ? user.getRole() : null, null);
    }
}
