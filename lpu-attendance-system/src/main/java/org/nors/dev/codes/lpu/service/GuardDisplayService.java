package org.nors.dev.codes.lpu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nors.dev.codes.lpu.dto.GuardDisplayResponse;
import org.nors.dev.codes.lpu.dto.GuardVideoResponse;
import org.nors.dev.codes.lpu.model.AppSetting;
import org.nors.dev.codes.lpu.model.GuardVideo;
import org.nors.dev.codes.lpu.repository.AppSettingRepository;
import org.nors.dev.codes.lpu.repository.GuardVideoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Controls what the guard gate display shows in its side panel:
 * live recent taps, an admin-uploaded video playlist, or nothing.
 */
@Service
public class GuardDisplayService {

    public static final String MODE_KEY = "guard.display.mode";
    public static final Set<String> MODES = Set.of("RECENT_TAPS", "VIDEO", "NONE");
    private static final String DEFAULT_MODE = "RECENT_TAPS";

    private static final Logger log = LogManager.getLogger(GuardDisplayService.class);

    private final AppSettingRepository settingRepository;
    private final GuardVideoRepository videoRepository;
    private final VideoStorageService videoStorage;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public GuardDisplayService(
            AppSettingRepository settingRepository,
            GuardVideoRepository videoRepository,
            VideoStorageService videoStorage,
            NotificationService notificationService,
            ObjectMapper objectMapper
    ) {
        this.settingRepository = settingRepository;
        this.videoRepository = videoRepository;
        this.videoStorage = videoStorage;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    public GuardDisplayResponse getDisplay() {
        String mode = settingRepository.findByKey(MODE_KEY)
                .map(AppSetting::getValue)
                .filter(MODES::contains)
                .orElse(DEFAULT_MODE);
        List<GuardVideoResponse> videos = videoRepository.findAllOrdered().stream()
                .map(GuardVideoResponse::from)
                .toList();
        return new GuardDisplayResponse(mode, videos);
    }

    public GuardDisplayResponse setMode(String rawMode) {
        String mode = rawMode == null ? "" : rawMode.trim().toUpperCase();
        if (!MODES.contains(mode)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "mode must be one of RECENT_TAPS, VIDEO, NONE"
            );
        }
        AppSetting setting = settingRepository.findByKey(MODE_KEY).orElseGet(() -> {
            AppSetting created = new AppSetting();
            created.setKey(MODE_KEY);
            return created;
        });
        setting.setValue(mode);
        setting.setUpdatedAt(java.time.Instant.now());
        settingRepository.save(setting);
        broadcastChange();
        return getDisplay();
    }

    public List<GuardVideoResponse> upload(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one video file is required");
        }
        for (MultipartFile file : files) {
            String path = videoStorage.store(file);
            GuardVideo video = new GuardVideo();
            video.setFilePath(path);
            video.setOriginalName(file.getOriginalFilename() != null ? file.getOriginalFilename() : path);
            video.setContentType(file.getContentType() != null ? file.getContentType() : "video/mp4");
            video.setSizeBytes(file.getSize());
            videoRepository.persist(video);
        }
        broadcastChange();
        return videoRepository.findAllOrdered().stream().map(GuardVideoResponse::from).toList();
    }

    public void delete(Long id) {
        GuardVideo video = videoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found"));
        videoRepository.delete(video);
        videoStorage.deleteFile(video.getFilePath());
        broadcastChange();
    }

    /** Lets always-on guard kiosks pick up display changes without a reload. */
    private void broadcastChange() {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "GUARD_DISPLAY_CHANGED");
            event.put("message", "Guard display settings updated");
            notificationService.broadcastRaw(objectMapper.writeValueAsString(event));
        } catch (Exception ex) {
            log.warn("Failed to broadcast guard display change", ex);
        }
    }
}
