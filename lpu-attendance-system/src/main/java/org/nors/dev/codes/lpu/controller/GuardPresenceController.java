package org.nors.dev.codes.lpu.controller;

import java.util.Map;
import org.nors.dev.codes.lpu.model.KioskGroup;
import org.nors.dev.codes.lpu.model.KioskGroups;
import org.nors.dev.codes.lpu.security.AuthenticatedUser;
import org.nors.dev.codes.lpu.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    public ResponseEntity<Map<String, Object>> online(@AuthenticationPrincipal AuthenticatedUser user) {
        KioskGroup group = KioskGroups.resolveForView(user != null ? user.getRole() : null, null);
        return ResponseEntity.ok(Map.of(
                "locations", notificationService.onlineKioskLocations(group),
                "kiosks", notificationService.onlineKiosksByGroup()
        ));
    }
}
