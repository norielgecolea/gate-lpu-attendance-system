package org.nors.dev.codes.lpu.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nors.dev.codes.lpu.config.UploadProperties;
import org.nors.dev.codes.lpu.dto.PhotoCleanupResponse;
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

    /**
     * Deletes {@code previousPublicPath} when it is a stored picture that is no longer the current path.
     */
    public void deleteIfReplaced(String previousPublicPath, String newPublicPath) {
        if (previousPublicPath == null || previousPublicPath.isBlank()) {
            return;
        }
        if (previousPublicPath.equals(newPublicPath)) {
            return;
        }
        deleteStoredPhoto(previousPublicPath);
    }

    /** Deletes a file under the pictures directory. Returns true when a file was removed. */
    public boolean deleteStoredPhoto(String publicPath) {
        Path file = resolveStoredPhoto(publicPath);
        if (file == null) {
            return false;
        }
        try {
            boolean deleted = Files.deleteIfExists(file);
            if (deleted) {
                log.info("Deleted replaced or unused photo {}", file.getFileName());
            }
            return deleted;
        } catch (IOException ex) {
            log.warn("Could not delete photo {}: {}", file, ex.toString());
            return false;
        }
    }

    /** Counts files on disk versus paths still referenced by student/employee records. */
    public PhotoCleanupResponse inspectUnused(Set<String> referencedPublicPaths) {
        UnusedScan scan = scanUnused(referencedPublicPaths);
        return new PhotoCleanupResponse(scan.referenced(), scan.onDisk(), scan.unused().size(), 0, 0);
    }

    /** Deletes picture files that no student or employee record still points to. */
    public PhotoCleanupResponse deleteUnused(Set<String> referencedPublicPaths) {
        UnusedScan scan = scanUnused(referencedPublicPaths);
        int deleted = 0;
        int failed = 0;
        for (Path file : scan.unused()) {
            try {
                if (Files.deleteIfExists(file)) {
                    deleted++;
                }
            } catch (IOException ex) {
                failed++;
                log.warn("Could not delete unused photo {}: {}", file, ex.toString());
            }
        }
        log.info(
                "Unused photo cleanup referenced={} onDisk={} deleted={} failed={}",
                scan.referenced(),
                scan.onDisk(),
                deleted,
                failed
        );
        return new PhotoCleanupResponse(
                scan.referenced(),
                Math.max(0, scan.onDisk() - deleted),
                Math.max(0, scan.unused().size() - deleted),
                deleted,
                failed
        );
    }

    private UnusedScan scanUnused(Set<String> referencedPublicPaths) {
        Set<String> referencedNames = new HashSet<>();
        if (referencedPublicPaths != null) {
            for (String path : referencedPublicPaths) {
                String filename = filenameFromPublicPath(path);
                if (filename != null) {
                    referencedNames.add(filename);
                }
            }
        }
        Set<Path> unused = new HashSet<>();
        int onDisk = 0;
        if (Files.isDirectory(picturesDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(picturesDir)) {
                for (Path file : stream) {
                    if (!Files.isRegularFile(file)) {
                        continue;
                    }
                    onDisk++;
                    if (!referencedNames.contains(file.getFileName().toString())) {
                        unused.add(file);
                    }
                }
            } catch (IOException ex) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Could not read pictures directory",
                        ex
                );
            }
        }
        return new UnusedScan(referencedNames.size(), onDisk, unused);
    }

    Path resolveStoredPhoto(String publicPath) {
        String filename = filenameFromPublicPath(publicPath);
        if (filename == null) {
            return null;
        }
        Path file = picturesDir.resolve(filename).normalize();
        if (!file.startsWith(picturesDir)) {
            return null;
        }
        return file;
    }

    static String filenameFromPublicPath(String publicPath) {
        if (publicPath == null || publicPath.isBlank()) {
            return null;
        }
        String trimmed = publicPath.trim();
        String prefix = "/pictures/";
        if (!trimmed.startsWith(prefix)) {
            return null;
        }
        String name = trimmed.substring(prefix.length());
        if (name.isBlank() || name.contains("/") || name.contains("\\") || name.contains("..")) {
            return null;
        }
        return name;
    }

    private record UnusedScan(int referenced, int onDisk, Set<Path> unused) {
        UnusedScan {
            unused = unused == null ? Set.of() : Set.copyOf(unused);
        }
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
