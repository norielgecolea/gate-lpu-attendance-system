package org.nors.dev.codes.lpu.controller;

import java.time.LocalDate;
import org.nors.dev.codes.lpu.dto.AuditLogPageResponse;
import org.nors.dev.codes.lpu.service.AuditLogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ResponseEntity<AuditLogPageResponse> page(
            @RequestParam(required = false) String personType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "200") int limit
    ) {
        return ResponseEntity.ok(auditLogService.page(personType, date, offset, limit));
    }
}
