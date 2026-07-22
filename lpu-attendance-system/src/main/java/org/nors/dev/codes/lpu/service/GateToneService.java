package org.nors.dev.codes.lpu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nors.dev.codes.lpu.dto.GateToneResponse;
import org.nors.dev.codes.lpu.dto.GateToneSettingsResponse;
import org.nors.dev.codes.lpu.model.AppSetting;
import org.nors.dev.codes.lpu.model.GateTone;
import org.nors.dev.codes.lpu.repository.AppSettingRepository;
import org.nors.dev.codes.lpu.repository.GateToneRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Manages uploaded gate tones and which tone is assigned to each event type.
 * Null / missing assignment means the kiosk uses its built-in default sound.
 */
@Service
public class GateToneService {

    public static final List<String> EVENT_TYPES = List.of(
            "TIME_IN",
            "TIME_OUT",
            "ERROR",
            "FINANCE_TAGGED",
            "BIRTHDAY"
    );

    private static final String SETTING_PREFIX = "gate.tone.";

    private static final Logger log = LogManager.getLogger(GateToneService.class);

    private final GateToneRepository toneRepository;
    private final AppSettingRepository settingRepository;
    private final ToneStorageService toneStorage;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public GateToneService(
            GateToneRepository toneRepository,
            AppSettingRepository settingRepository,
            ToneStorageService toneStorage,
            NotificationService notificationService,
            ObjectMapper objectMapper
    ) {
        this.toneRepository = toneRepository;
        this.settingRepository = settingRepository;
        this.toneStorage = toneStorage;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public GateToneSettingsResponse getSettings() {
        List<GateToneResponse> tones = toneRepository.findAllOrdered().stream()
                .map(GateToneResponse::from)
                .toList();
        return new GateToneSettingsResponse(tones, readAssignments());
    }

    @Transactional
    public List<GateToneResponse> upload(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one audio file is required");
        }
        for (MultipartFile file : files) {
            ToneStorageService.StoredTone stored = toneStorage.store(file);
            GateTone tone = new GateTone();
            tone.setFilePath(stored.path());
            tone.setOriginalName(file.getOriginalFilename() != null ? file.getOriginalFilename() : stored.path());
            tone.setContentType(stored.contentType());
            tone.setSizeBytes(stored.sizeBytes());
            tone.setUploadedAt(Instant.now());
            toneRepository.persist(tone);
        }
        broadcastChange();
        return toneRepository.findAllOrdered().stream().map(GateToneResponse::from).toList();
    }

    @Transactional
    public void delete(Long id) {
        GateTone tone = toneRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tone not found"));
        clearAssignmentsForTone(id);
        toneRepository.delete(tone);
        toneStorage.deleteFile(tone.getFilePath());
        broadcastChange();
    }

    @Transactional
    public GateToneSettingsResponse setAssignments(Map<String, Object> body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignments body is required");
        }
        for (String eventType : EVENT_TYPES) {
            if (!body.containsKey(eventType)) {
                continue;
            }
            Object raw = body.get(eventType);
            String rawId = raw == null ? "" : String.valueOf(raw).trim();
            if (rawId.isEmpty() || "null".equalsIgnoreCase(rawId)) {
                clearSetting(SETTING_PREFIX + eventType);
                continue;
            }
            Long toneId;
            try {
                toneId = Long.parseLong(rawId);
            } catch (NumberFormatException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid tone id for " + eventType);
            }
            if (toneRepository.findById(toneId).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown tone id for " + eventType);
            }
            saveSetting(SETTING_PREFIX + eventType, String.valueOf(toneId));
        }
        broadcastChange();
        return getSettings();
    }

    private Map<String, String> readAssignments() {
        Map<String, String> assignments = new LinkedHashMap<>();
        for (String eventType : EVENT_TYPES) {
            assignments.put(eventType, settingRepository.findByKey(SETTING_PREFIX + eventType)
                    .map(AppSetting::getValue)
                    .filter(value -> !value.isBlank())
                    .orElse(null));
        }
        return assignments;
    }

    private void clearAssignmentsForTone(Long toneId) {
        String id = String.valueOf(toneId);
        for (String eventType : EVENT_TYPES) {
            String key = SETTING_PREFIX + eventType;
            settingRepository.findByKey(key).ifPresent(setting -> {
                if (id.equals(setting.getValue())) {
                    clearSetting(key);
                }
            });
        }
    }

    private void saveSetting(String key, String value) {
        AppSetting setting = settingRepository.findByKey(key).orElseGet(() -> {
            AppSetting created = new AppSetting();
            created.setKey(key);
            return created;
        });
        setting.setValue(value);
        setting.setUpdatedAt(Instant.now());
        settingRepository.save(setting);
    }

    private void clearSetting(String key) {
        settingRepository.findByKey(key).ifPresent(setting -> {
            setting.setValue("");
            setting.setUpdatedAt(Instant.now());
            settingRepository.save(setting);
        });
    }

    private void broadcastChange() {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "GATE_TONES_CHANGED");
            event.put("message", "Gate tones updated");
            notificationService.broadcastRaw(objectMapper.writeValueAsString(event));
        } catch (Exception ex) {
            log.warn("Failed to broadcast gate tones change", ex);
        }
    }
}
