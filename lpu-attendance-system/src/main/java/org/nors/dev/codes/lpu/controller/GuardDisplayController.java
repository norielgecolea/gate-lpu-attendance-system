package org.nors.dev.codes.lpu.controller;

import java.util.List;
import java.util.Map;
import org.nors.dev.codes.lpu.dto.GuardDisplayResponse;
import org.nors.dev.codes.lpu.dto.GuardVideoResponse;
import org.nors.dev.codes.lpu.service.GuardDisplayService;
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
@RequestMapping("/api/guard-display")
public class GuardDisplayController {

    private final GuardDisplayService guardDisplayService;

    public GuardDisplayController(GuardDisplayService guardDisplayService) {
        this.guardDisplayService = guardDisplayService;
    }

    @GetMapping
    public ResponseEntity<GuardDisplayResponse> getDisplay() {
        return ResponseEntity.ok(guardDisplayService.getDisplay());
    }

    @PutMapping("/mode")
    public ResponseEntity<GuardDisplayResponse> setMode(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(guardDisplayService.setMode(body.get("mode")));
    }

    @PostMapping("/videos")
    public ResponseEntity<List<GuardVideoResponse>> upload(
            @RequestParam("files") List<MultipartFile> files
    ) {
        return ResponseEntity.ok(guardDisplayService.upload(files));
    }

    @DeleteMapping("/videos/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        guardDisplayService.delete(id);
        return ResponseEntity.ok(Map.of("message", "Video deleted"));
    }
}
