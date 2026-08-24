package org.nors.dev.codes.lpu.controller;

import org.nors.dev.codes.lpu.dto.ServerTimeResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public clock source for kiosk and monitor UIs — server instant, campus timezone. */
@RestController
@RequestMapping("/api/kiosk")
public class KioskTimeController {

    @GetMapping("/time")
    public ResponseEntity<ServerTimeResponse> time() {
        return ResponseEntity.ok(ServerTimeResponse.systemNow());
    }
}
