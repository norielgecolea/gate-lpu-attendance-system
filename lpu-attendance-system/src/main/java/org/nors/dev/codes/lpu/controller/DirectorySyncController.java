package org.nors.dev.codes.lpu.controller;

import java.time.LocalDate;
import org.nors.dev.codes.lpu.dto.ErpEmployeeAttendanceResponse;
import org.nors.dev.codes.lpu.dto.SyncDeletionResponse;
import org.nors.dev.codes.lpu.dto.SyncEmployeeResponse;
import org.nors.dev.codes.lpu.dto.SyncPageResponse;
import org.nors.dev.codes.lpu.dto.SyncStudentResponse;
import org.nors.dev.codes.lpu.service.DirectorySyncService;
import org.nors.dev.codes.lpu.service.ErpAttendanceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sync")
public class DirectorySyncController {

    private final DirectorySyncService directorySyncService;
    private final ErpAttendanceService erpAttendanceService;

    public DirectorySyncController(
            DirectorySyncService directorySyncService,
            ErpAttendanceService erpAttendanceService
    ) {
        this.directorySyncService = directorySyncService;
        this.erpAttendanceService = erpAttendanceService;
    }

    @GetMapping("/students")
    public ResponseEntity<SyncPageResponse<SyncStudentResponse>> students(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(directorySyncService.students(cursor, limit));
    }

    @GetMapping("/employees")
    public ResponseEntity<SyncPageResponse<SyncEmployeeResponse>> employees(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(directorySyncService.employees(cursor, limit));
    }

    @GetMapping("/deletions")
    public ResponseEntity<SyncPageResponse<SyncDeletionResponse>> deletions(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(directorySyncService.deletions(cursor, limit));
    }

    /**
     * Main-gate employee attendance for the ERP. One record per employee per day.
     */
    @GetMapping("/employee-attendance")
    public ResponseEntity<ErpEmployeeAttendanceResponse> employeeAttendance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String employeeNo,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(
                erpAttendanceService.employeeMainGateLogs(startDate, endDate, employeeNo, offset, limit)
        );
    }
}
