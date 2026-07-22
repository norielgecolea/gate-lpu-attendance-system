package org.nors.dev.codes.lpu.controller;

import java.util.Map;
import org.nors.dev.codes.lpu.service.RfidUniquenessService;
import org.nors.dev.codes.lpu.service.RfidUniquenessService.OwnerType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rfid")
public class RfidController {

    private final RfidUniquenessService rfidUniquenessService;

    public RfidController(RfidUniquenessService rfidUniquenessService) {
        this.rfidUniquenessService = rfidUniquenessService;
    }

    /**
     * Pre-save check used by management forms and RFID registration pages.
     * Pass ownerType + excludeId when editing so the current record is ignored.
     */
    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> check(
            @RequestParam String rfid,
            @RequestParam(required = false) String ownerType,
            @RequestParam(required = false) Long excludeId
    ) {
        OwnerType owner = parseOwnerType(ownerType);
        String conflict = rfidUniquenessService.findConflictMessage(rfid, owner, excludeId);
        if (conflict == null) {
            return ResponseEntity.ok(Map.of("available", true));
        }
        return ResponseEntity.ok(Map.of(
                "available", false,
                "message", conflict
        ));
    }

    private static OwnerType parseOwnerType(String ownerType) {
        if (ownerType == null || ownerType.isBlank()) {
            return null;
        }
        return OwnerType.valueOf(ownerType.trim().toUpperCase());
    }
}
