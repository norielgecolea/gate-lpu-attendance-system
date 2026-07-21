package org.nors.dev.codes.lpu.service;

import java.io.IOException;
import java.io.InputStream;
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
public class PhotoStorageService {

    private static final Logger log = LogManager.getLogger(PhotoStorageService.class);
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private final Path picturesDir;
    private final ImageOptimizationService imageOptimizationService;

    public PhotoStorageService(
            UploadProperties uploadProperties,
            ImageOptimizationService imageOptimizationService
    ) {
        this.picturesDir = Paths.get(uploadProperties.getPicturesDir()).toAbsolutePath().normalize();
        this.imageOptimizationService = imageOptimizationService;
        try {
            Files.createDirectories(this.picturesDir);
            log.info("Student pictures directory: {}", this.picturesDir);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create pictures directory: " + this.picturesDir, ex);
        }
    }

    /**
     * Saves an image under the pictures folder and returns a public path like {@code /pictures/uuid.jpg}.
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Photo file is required");
        }

        String contentType = resolveContentType(file);
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only JPEG, PNG, WebP, or GIF images are allowed"
            );
        }

        String filename = UUID.randomUUID() + ".jpg";
        Path destination = picturesDir.resolve(filename).normalize();
        if (!destination.startsWith(picturesDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file path");
        }

        boolean optimized = false;
        if (imageOptimizationService.canOptimize(contentType)) {
            try (InputStream input = file.getInputStream()) {
                optimized = imageOptimizationService.optimizeToJpeg(input, destination);
            } catch (IOException ex) {
                log.warn("Unable to read uploaded photo for optimization: {}", ex.toString());
            }
        }

        if (!optimized) {
            filename = UUID.randomUUID() + extensionFor(contentType, file.getOriginalFilename());
            destination = picturesDir.resolve(filename).normalize();
            if (!destination.startsWith(picturesDir)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file path");
            }
            try {
                file.transferTo(destination);
            } catch (IOException ex) {
                log.error("Failed to store photo {}", filename, ex);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store photo");
            }
        }

        log.info("Stored photo {} (optimized={})", filename, optimized);
        return "/pictures/" + filename;
    }

    /** Returns true when the file looks like an allowed image (by content type or extension). */
    public boolean isAllowedImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        String contentType = resolveContentType(file);
        return contentType != null && ALLOWED_CONTENT_TYPES.contains(contentType);
    }

    /**
     * Extracts the person number from a photo filename (basename without extension).
     * Example: {@code 2021-0001.jpg} → {@code 2021-0001}.
     */
    public static String personNumberFromFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return null;
        }
        String name = originalFilename.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        name = name.trim();
        return name.isEmpty() ? null : name;
    }

    private static String resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null) {
            String normalized = contentType.toLowerCase(Locale.ROOT).trim();
            if (ALLOWED_CONTENT_TYPES.contains(normalized)) {
                return normalized;
            }
            // Some browsers send octet-stream for bulk picks — fall through to extension.
            if (!normalized.equals("application/octet-stream") && !normalized.isBlank()) {
                return normalized;
            }
        }
        return contentTypeFromFilename(file.getOriginalFilename());
    }

    private static String contentTypeFromFilename(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return null;
        }
        String ext = originalFilename.substring(originalFilename.lastIndexOf('.') + 1)
                .toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            default -> null;
        };
    }

    private static String extensionFor(String contentType, String originalFilename) {
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> {
                if (originalFilename != null && originalFilename.contains(".")) {
                    yield originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
                }
                yield ".bin";
            }
        };
    }
}
