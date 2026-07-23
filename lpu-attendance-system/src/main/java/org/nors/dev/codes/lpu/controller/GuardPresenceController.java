package org.nors.dev.codes.lpu.controller;

import java.util.List;
import java.util.Map;
import org.nors.dev.codes.lpu.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reports which guard gate locations currently have an open kiosk WebSocket. */
@RestController
@RequestMapping("/api/guards")
public class GuardPresenceController {

    private final NotificationService notificationService;

    public GuardPresenceController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/online")
    public ResponseEntity<Map<String, List<String>>> online() {
        return ResponseEntity.ok(Map.of("locations", notificationService.onlineGuardLocations()));
    }
}
