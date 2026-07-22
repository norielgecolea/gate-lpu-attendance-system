package org.nors.dev.codes.lpu.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nors.dev.codes.lpu.config.UploadProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ToneStorageService {

    private static final Logger log = LogManager.getLogger(ToneStorageService.class);
    private static final double MAX_DURATION_SECONDS = 10.0;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "audio/mpeg",
            "audio/mp3",
            "audio/wav",
            "audio/x-wav",
            "audio/wave",
            "audio/ogg",
            "audio/webm",
            "audio/mp4",
            "audio/aac",
            "audio/x-m4a"
    );

    private final Path tonesDir;
    private final String ffprobePath;
    private volatile Boolean ffprobeAvailable;

    public ToneStorageService(UploadProperties uploadProperties) {
        this.tonesDir = Paths.get(uploadProperties.getTonesDir()).toAbsolutePath().normalize();
        this.ffprobePath = deriveFfprobePath(uploadProperties.getFfmpegPath());
        try {
            Files.createDirectories(this.tonesDir);
            log.info("Gate tones directory: {}", this.tonesDir);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create tones directory: " + this.tonesDir, ex);
        }
    }

    /** Saves an audio file and returns a public path like {@code /tones/uuid.mp3}. */
    public StoredTone store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Audio file is required");
        }

        String contentType = normalizeContentType(file.getContentType());
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            // Some browsers omit content type — fall back to extension check.
            String byName = contentTypeFromFilename(file.getOriginalFilename());
            if (byName == null || !ALLOWED_CONTENT_TYPES.contains(byName)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Only MP3, WAV, OGG, WebM, AAC, or M4A audio files are allowed"
                );
            }
            contentType = byName;
        }

        Path temp = null;
        try {
            String extension = extensionFor(contentType, file.getOriginalFilename());
            temp = Files.createTempFile("gate-tone-upload-", extension);
            file.transferTo(temp);

            Double duration = probeDurationSeconds(temp);
            if (duration != null && duration > MAX_DURATION_SECONDS) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Tone must be 10 seconds or shorter (got "
                                + String.format(Locale.ROOT, "%.1f", duration)
                                + "s)"
                );
            }

            String filename = UUID.randomUUID() + extension;
            Path destination = tonesDir.resolve(filename).normalize();
            if (!destination.startsWith(tonesDir)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file path");
            }
            Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
            temp = null;
            log.info("Stored gate tone {}", filename);
            return new StoredTone("/tones/" + filename, contentType, Files.size(destination));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IOException ex) {
            log.error("Failed to store tone", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store audio");
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ex) {
                    log.warn("Failed to delete temp tone upload {}", temp, ex);
                }
            }
        }
    }

    public void deleteFile(String publicPath) {
        if (publicPath == null || !publicPath.startsWith("/tones/")) {
            return;
        }
        Path target = tonesDir.resolve(publicPath.substring("/tones/".length())).normalize();
        if (!target.startsWith(tonesDir)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            log.warn("Failed to delete tone file {}", target, ex);
        }
    }

    /** @return duration in seconds, or null when ffprobe is unavailable / fails */
    private Double probeDurationSeconds(Path file) {
        if (!isFfprobeAvailable()) {
            return null;
        }
        try {
            Process process = new ProcessBuilder(
                    ffprobePath,
                    "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    file.toAbsolutePath().toString()
            ).redirectErrorStream(true).start();

            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
            )) {
                output = reader.readLine();
            }
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0 || output == null || output.isBlank()) {
                return null;
            }
            return Double.parseDouble(output.trim());
        } catch (Exception ex) {
            log.warn("Could not probe tone duration for {}", file, ex);
            return null;
        }
    }

    private boolean isFfprobeAvailable() {
        Boolean cached = ffprobeAvailable;
        if (cached != null) {
            return cached;
        }
        try {
            Process process = new ProcessBuilder(ffprobePath, "-version").start();
            boolean ok = process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
            ffprobeAvailable = ok;
            if (!ok) {
                log.info("ffprobe not available at '{}' — tone duration is validated in the browser only", ffprobePath);
            }
            return ok;
        } catch (Exception ex) {
            ffprobeAvailable = false;
            log.info("ffprobe not available at '{}' — tone duration is validated in the browser only", ffprobePath);
            return false;
        }
    }

    private static String deriveFfprobePath(String ffmpegPath) {
        if (ffmpegPath == null || ffmpegPath.isBlank()) {
            return "ffprobe";
        }
        if (ffmpegPath.endsWith("ffmpeg") || ffmpegPath.endsWith("ffmpeg.exe")) {
            return ffmpegPath.replace("ffmpeg", "ffprobe");
        }
        return "ffprobe";
    }

    private static String normalizeContentType(String contentType) {
        return contentType == null ? null : contentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
    }

    private static String contentTypeFromFilename(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return null;
        }
        String ext = originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case ".mp3" -> "audio/mpeg";
            case ".wav" -> "audio/wav";
            case ".ogg" -> "audio/ogg";
            case ".webm" -> "audio/webm";
            case ".m4a" -> "audio/mp4";
            case ".aac" -> "audio/aac";
            default -> null;
        };
    }

    private static String extensionFor(String contentType, String originalFilename) {
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "audio/mpeg", "audio/mp3" -> ".mp3";
            case "audio/wav", "audio/x-wav", "audio/wave" -> ".wav";
            case "audio/ogg" -> ".ogg";
            case "audio/webm" -> ".webm";
            case "audio/mp4", "audio/x-m4a" -> ".m4a";
            case "audio/aac" -> ".aac";
            default -> {
                if (originalFilename != null && originalFilename.contains(".")) {
                    yield originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
                }
                yield ".bin";
            }
        };
    }

    public record StoredTone(String path, String contentType, long sizeBytes) {
    }
}
