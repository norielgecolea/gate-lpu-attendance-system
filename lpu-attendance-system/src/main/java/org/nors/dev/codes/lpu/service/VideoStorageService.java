package org.nors.dev.codes.lpu.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nors.dev.codes.lpu.config.UploadProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VideoStorageService {

    private static final Logger log = LogManager.getLogger(VideoStorageService.class);
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "video/mp4",
            "video/webm",
            "video/ogg",
            "video/quicktime"
    );

    private final Path videosDir;

    public VideoStorageService(UploadProperties uploadProperties) {
        this.videosDir = Paths.get(uploadProperties.getVideosDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.videosDir);
            log.info("Guard videos directory: {}", this.videosDir);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create videos directory: " + this.videosDir, ex);
        }
    }

    /**
     * Saves a video under the videos folder and returns a public path like {@code /videos/uuid.mp4}.
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Video file is required");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only MP4, WebM, Ogg, or QuickTime videos are allowed"
            );
        }

        String extension = extensionFor(contentType, file.getOriginalFilename());
        String filename = UUID.randomUUID() + extension;
        Path destination = videosDir.resolve(filename).normalize();
        if (!destination.startsWith(videosDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file path");
        }

        try {
            file.transferTo(destination);
        } catch (IOException ex) {
            log.error("Failed to store video {}", filename, ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store video");
        }

        log.info("Stored guard video {}", filename);
        return "/videos/" + filename;
    }

    /** Removes a stored video file; missing files are ignored. */
    public void deleteFile(String publicPath) {
        if (publicPath == null || !publicPath.startsWith("/videos/")) {
            return;
        }
        Path target = videosDir.resolve(publicPath.substring("/videos/".length())).normalize();
        if (!target.startsWith(videosDir)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            log.warn("Failed to delete video file {}", target, ex);
        }
    }

    private static String extensionFor(String contentType, String originalFilename) {
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "video/mp4" -> ".mp4";
            case "video/webm" -> ".webm";
            case "video/ogg" -> ".ogv";
            case "video/quicktime" -> ".mov";
            default -> {
                if (originalFilename != null && originalFilename.contains(".")) {
                    yield originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
                }
                yield ".bin";
            }
        };
    }
}
