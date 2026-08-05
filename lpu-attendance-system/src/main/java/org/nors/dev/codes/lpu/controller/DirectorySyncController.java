package org.nors.dev.codes.lpu.controller;

import org.nors.dev.codes.lpu.dto.SyncDeletionResponse;
import org.nors.dev.codes.lpu.dto.SyncEmployeeResponse;
import org.nors.dev.codes.lpu.dto.SyncPageResponse;
import org.nors.dev.codes.lpu.dto.SyncStudentResponse;
import org.nors.dev.codes.lpu.service.DirectorySyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sync")
public class DirectorySyncController {

    private final DirectorySyncService directorySyncService;

    public DirectorySyncController(DirectorySyncService directorySyncService) {
        this.directorySyncService = directorySyncService;
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
}
