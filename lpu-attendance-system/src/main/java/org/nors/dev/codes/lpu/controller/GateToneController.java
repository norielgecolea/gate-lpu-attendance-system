package org.nors.dev.codes.lpu.controller;

import java.util.List;
import java.util.Map;
import org.nors.dev.codes.lpu.dto.GateToneResponse;
import org.nors.dev.codes.lpu.dto.GateToneSettingsResponse;
import org.nors.dev.codes.lpu.service.GateToneService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/gate-tones")
public class GateToneController {

    private final GateToneService gateToneService;

    public GateToneController(GateToneService gateToneService) {
        this.gateToneService = gateToneService;
    }

    @GetMapping
    public ResponseEntity<GateToneSettingsResponse> getSettings() {
        return ResponseEntity.ok(gateToneService.getSettings());
    }

    @PostMapping
    public ResponseEntity<List<GateToneResponse>> upload(
            @RequestParam("files") List<MultipartFile> files
    ) {
        return ResponseEntity.ok(gateToneService.upload(files));
    }

    @PutMapping("/assignments")
    public ResponseEntity<GateToneSettingsResponse> setAssignments(
            @RequestBody Map<String, Object> body
    ) {
        return ResponseEntity.ok(gateToneService.setAssignments(body));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        gateToneService.delete(id);
        return ResponseEntity.ok(Map.of("message", "Tone deleted"));
    }
}
