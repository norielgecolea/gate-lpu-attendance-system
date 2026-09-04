package org.nors.dev.codes.lpu.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.nors.dev.codes.lpu.dto.TapErrorLogResponse;
import org.nors.dev.codes.lpu.model.KioskGroup;
import org.nors.dev.codes.lpu.model.KioskGroups;
import org.nors.dev.codes.lpu.security.AuthenticatedUser;
import org.nors.dev.codes.lpu.service.TapErrorLogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tap-errors")
public class TapErrorLogController {

    private final TapErrorLogService tapErrorLogService;

    public TapErrorLogController(TapErrorLogService tapErrorLogService) {
        this.tapErrorLogService = tapErrorLogService;
    }

    @GetMapping
    public ResponseEntity<List<TapErrorLogResponse>> list(
            @RequestParam(defaultValue = "500") int limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ResponseEntity.ok(tapErrorLogService.list(limit, date, viewGroup(user)));
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> count(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ResponseEntity.ok(Map.of("count", tapErrorLogService.count(date, viewGroup(user))));
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Object>> clear(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        KioskGroup group = viewGroup(user);
        int deleted = date != null ? tapErrorLogService.clearDate(date, group) : tapErrorLogService.clearAll(group);
        return ResponseEntity.ok(Map.of("message", "Tap error logs cleared", "deleted", deleted));
    }

    private static KioskGroup viewGroup(AuthenticatedUser user) {
        return KioskGroups.resolveForView(user != null ? user.getRole() : null, null);
    }
}
