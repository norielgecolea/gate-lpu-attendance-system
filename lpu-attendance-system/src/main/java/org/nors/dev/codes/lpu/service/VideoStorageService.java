package org.nors.dev.codes.lpu.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
    private final VideoOptimizationService videoOptimizationService;

    public VideoStorageService(
            UploadProperties uploadProperties,
            VideoOptimizationService videoOptimizationService
    ) {
        this.videosDir = Paths.get(uploadProperties.getVideosDir()).toAbsolutePath().normalize();
        this.videoOptimizationService = videoOptimizationService;
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
    public StoredVideo store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Video file is required");
        }

        String contentType = normalizeContentType(file.getContentType());
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only MP4, WebM, Ogg, or QuickTime videos are allowed"
            );
        }

        Path tempSource = null;
        try {
            tempSource = Files.createTempFile("guard-video-upload-", ".bin");
            file.transferTo(tempSource);

            String optimizedFilename = UUID.randomUUID() + ".mp4";
            Path optimizedDestination = videosDir.resolve(optimizedFilename).normalize();
            if (!optimizedDestination.startsWith(videosDir)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file path");
            }

            if (videoOptimizationService.transcodeToMp4(tempSource, optimizedDestination)) {
                return new StoredVideo(
                        "/videos/" + optimizedFilename,
                        "video/mp4",
                        Files.size(optimizedDestination)
                );
            }

            String extension = extensionFor(contentType, file.getOriginalFilename());
            String filename = UUID.randomUUID() + extension;
            Path destination = videosDir.resolve(filename).normalize();
            if (!destination.startsWith(videosDir)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file path");
            }
            Files.move(tempSource, destination, StandardCopyOption.REPLACE_EXISTING);
            tempSource = null;
            log.info("Stored guard video {} without transcoding", filename);
            return new StoredVideo(
                    "/videos/" + filename,
                    contentType,
                    Files.size(destination)
            );
        } catch (IOException ex) {
            log.error("Failed to store video", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store video");
        } finally {
            if (tempSource != null) {
                try {
                    Files.deleteIfExists(tempSource);
                } catch (IOException ex) {
                    log.warn("Failed to delete temp video upload {}", tempSource, ex);
                }
            }
        }
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

    private static String normalizeContentType(String contentType) {
        return contentType == null ? null : contentType.toLowerCase(Locale.ROOT);
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

    public record StoredVideo(String path, String contentType, long sizeBytes) {
    }
}
