package org.nors.dev.codes.lpu.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nors.dev.codes.lpu.config.UploadProperties;
import org.springframework.stereotype.Service;

/**
 * Transcodes uploaded guard-display videos to H.264 MP4 when FFmpeg is available on the host.
 */
@Service
public class VideoOptimizationService {

    private static final Logger log = LogManager.getLogger(VideoOptimizationService.class);
    private static final long FFMPEG_TIMEOUT_MINUTES = 15;

    private final boolean enabled;
    private final String ffmpegPath;
    private final int maxWidth;
    private final int crf;
    private volatile Boolean ffmpegAvailable;

    public VideoOptimizationService(UploadProperties uploadProperties) {
        this.enabled = uploadProperties.isVideoOptimizationEnabled();
        this.ffmpegPath = uploadProperties.getFfmpegPath();
        this.maxWidth = Math.max(uploadProperties.getVideoMaxWidth(), 320);
        this.crf = clampCrf(uploadProperties.getVideoCrf());
    }

    /**
     * Transcodes {@code source} into an MP4 at {@code destination}.
     *
     * @return true when FFmpeg produced an output file
     */
    public boolean transcodeToMp4(Path source, Path destination) {
        if (!enabled || !isFfmpegAvailable()) {
            return false;
        }
        if (!Files.isRegularFile(source)) {
            return false;
        }

        try {
            Files.deleteIfExists(destination);
        } catch (IOException ex) {
            log.warn("Could not clear previous transcode output {}: {}", destination, ex.toString());
            return false;
        }

        List<String> command = List.of(
                ffmpegPath,
                "-y",
                "-i", source.toAbsolutePath().toString(),
                "-vf", "scale='min(" + maxWidth + ",iw)':-2",
                "-c:v", "libx264",
                "-preset", "medium",
                "-crf", String.valueOf(crf),
                "-c:a", "aac",
                "-b:a", "128k",
                "-movflags", "+faststart",
                destination.toAbsolutePath().toString()
        );

        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(FFMPEG_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                log.warn("FFmpeg transcoding timed out for {}", source.getFileName());
                Files.deleteIfExists(destination);
                return false;
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(destination) || Files.size(destination) == 0) {
                log.warn("FFmpeg transcoding failed (exit={}): {}", process.exitValue(), trimLog(output));
                Files.deleteIfExists(destination);
                return false;
            }
            log.info(
                    "Transcoded video {} ({} bytes) -> {} ({} bytes)",
                    source.getFileName(),
                    Files.size(source),
                    destination.getFileName(),
                    Files.size(destination)
            );
            return true;
        } catch (IOException ex) {
            log.warn("FFmpeg transcoding error for {}: {}", source.getFileName(), ex.toString());
            try {
                Files.deleteIfExists(destination);
            } catch (IOException ignored) {
                // best effort cleanup
            }
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("FFmpeg transcoding interrupted for {}", source.getFileName());
            try {
                Files.deleteIfExists(destination);
            } catch (IOException ignored) {
                // best effort cleanup
            }
            return false;
        }
    }

    public boolean isFfmpegAvailable() {
        if (!enabled) {
            return false;
        }
        Boolean cached = ffmpegAvailable;
        if (cached != null) {
            return cached;
        }
        boolean available = probeFfmpeg();
        ffmpegAvailable = available;
        if (!available) {
            log.info(
                    "FFmpeg not available at '{}' — guard videos will be stored without transcoding",
                    ffmpegPath
            );
        }
        return available;
    }

    private boolean probeFfmpeg() {
        try {
            Process process = new ProcessBuilder(ffmpegPath, "-version").start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private static int clampCrf(int crf) {
        if (crf < 18) {
            return 18;
        }
        if (crf > 35) {
            return 35;
        }
        return crf;
    }

    private static String trimLog(String output) {
        if (output == null || output.isBlank()) {
            return "";
        }
        String trimmed = output.strip();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) + "…" : trimmed;
    }
}
