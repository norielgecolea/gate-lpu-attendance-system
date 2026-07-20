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
public class PhotoStorageService {

    private static final Logger log = LogManager.getLogger(PhotoStorageService.class);
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private final Path picturesDir;

    public PhotoStorageService(UploadProperties uploadProperties) {
        this.picturesDir = Paths.get(uploadProperties.getPicturesDir()).toAbsolutePath().normalize();
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

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only JPEG, PNG, WebP, or GIF images are allowed"
            );
        }

        String extension = extensionFor(contentType, file.getOriginalFilename());
        String filename = UUID.randomUUID() + extension;
        Path destination = picturesDir.resolve(filename).normalize();
        if (!destination.startsWith(picturesDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file path");
        }

        try {
            file.transferTo(destination);
        } catch (IOException ex) {
            log.error("Failed to store photo {}", filename, ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store photo");
        }

        log.info("Stored student photo {}", filename);
        return "/pictures/" + filename;
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
