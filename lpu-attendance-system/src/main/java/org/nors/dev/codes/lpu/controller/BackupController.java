package org.nors.dev.codes.lpu.controller;

import org.nors.dev.codes.lpu.dto.BackupRestoreResponse;
import org.nors.dev.codes.lpu.dto.PhotoCleanupResponse;
import org.nors.dev.codes.lpu.service.BackupService;
import org.nors.dev.codes.lpu.service.PhotoCleanupService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/backup")
public class BackupController {

    private static final MediaType ZIP = MediaType.parseMediaType("application/zip");

    private final BackupService backupService;
    private final PhotoCleanupService photoCleanupService;

    public BackupController(BackupService backupService, PhotoCleanupService photoCleanupService) {
        this.backupService = backupService;
        this.photoCleanupService = photoCleanupService;
    }

    @GetMapping
    public ResponseEntity<StreamingResponseBody> download() {
        BackupService.BackupDownload download = backupService.startDownload();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, download.contentDisposition())
                .contentType(ZIP)
                .contentLength(download.contentLength())
                .body(download.body());
    }

    @PostMapping("/restore")
    public ResponseEntity<BackupRestoreResponse> restore(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(backupService.restore(file));
    }

    @GetMapping("/pictures/unused")
    public ResponseEntity<PhotoCleanupResponse> unusedPictures() {
        return ResponseEntity.ok(photoCleanupService.preview());
    }

    @PostMapping("/pictures/cleanup")
    public ResponseEntity<PhotoCleanupResponse> cleanupUnusedPictures() {
        return ResponseEntity.ok(photoCleanupService.deleteUnused());
    }
}
