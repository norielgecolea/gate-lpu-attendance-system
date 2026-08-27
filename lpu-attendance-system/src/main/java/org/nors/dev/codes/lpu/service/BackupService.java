package org.nors.dev.codes.lpu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nors.dev.codes.lpu.dto.BackupManifest;
import org.nors.dev.codes.lpu.dto.BackupRestoreResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Service
public class BackupService {

    private static final Logger log = LogManager.getLogger(BackupService.class);
    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");
    private static final DateTimeFormatter FILENAME_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final List<String> INCLUDED_PATHS = List.of(
            MediaBackupService.DATABASE_PREFIX + "/",
            MediaBackupService.PICTURES_PREFIX + "/",
            MediaBackupService.VIDEOS_PREFIX + "/",
            MediaBackupService.TONES_PREFIX + "/"
    );

    private final DatabaseBackupService databaseBackupService;
    private final MediaBackupService mediaBackupService;
    private final ObjectMapper objectMapper;
    private final ReentrantLock lock = new ReentrantLock();
    private final String appVersion;

    public BackupService(
            DatabaseBackupService databaseBackupService,
            MediaBackupService mediaBackupService,
            ObjectMapper objectMapper
    ) {
        this.databaseBackupService = databaseBackupService;
        this.mediaBackupService = mediaBackupService;
        this.objectMapper = objectMapper;
        String version = getClass().getPackage().getImplementationVersion();
        this.appVersion = version == null || version.isBlank() ? "dev" : version;
    }

    public BackupDownload startDownload() {
        if (!lock.tryLock()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A backup or restore is already in progress"
            );
        }
        Path dumpDir = null;
        boolean transferred = false;
        try {
            dumpDir = Files.createTempDirectory("lpu-backup-db-");
            databaseBackupService.dumpToDirectory(dumpDir);
            Path dump = dumpDir;
            StreamingResponseBody body = output -> {
                try {
                    writeZip(output, dump);
                } finally {
                    deleteQuietly(dump);
                    lock.unlock();
                }
            };
            transferred = true;
            dumpDir = null;
            return new BackupDownload(backupFilename(), body);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create backup", ex);
        } finally {
            if (!transferred) {
                deleteQuietly(dumpDir);
                lock.unlock();
            }
        }
    }

    public BackupRestoreResponse restore(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Backup zip file is required");
        }
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!originalName.endsWith(".zip")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Backup must be a .zip file");
        }
        if (!lock.tryLock()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A backup or restore is already in progress"
            );
        }
        Path upload = null;
        Path staging = null;
        try {
            upload = Files.createTempFile("lpu-restore-", ".zip");
            file.transferTo(upload);
            staging = Files.createTempDirectory("lpu-restore-staging-");
            MediaBackupService.ExtractedBackup extracted = mediaBackupService.extractZip(upload, staging);
            validateManifest(extracted.manifestFile());
            databaseBackupService.restoreFromDirectory(extracted.databaseDir());
            int pictures;
            int videos;
            int tones;
            try {
                pictures = mediaBackupService.replaceDirectory(mediaBackupService.picturesDir(), extracted.picturesDir());
                videos = mediaBackupService.replaceDirectory(mediaBackupService.videosDir(), extracted.videosDir());
                tones = mediaBackupService.replaceDirectory(mediaBackupService.tonesDir(), extracted.tonesDir());
            } catch (IOException ex) {
                log.error("Database was restored but media replacement failed", ex);
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Database was restored but photos, videos, or tones could not be replaced"
                                + (ex.getMessage() != null ? ": " + ex.getMessage() : ""),
                        ex
                );
            }
            return new BackupRestoreResponse(true, pictures, videos, tones);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not restore backup", ex);
        } finally {
            deleteQuietly(upload);
            deleteQuietly(staging);
            lock.unlock();
        }
    }

    void writeZip(OutputStream output, Path dumpDir) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(MediaBackupService.MANIFEST_ENTRY));
            zip.write(objectMapper.writeValueAsBytes(new BackupManifest(
                    BackupManifest.CURRENT_FORMAT_VERSION,
                    Instant.now().toString(),
                    appVersion,
                    INCLUDED_PATHS
            )));
            zip.closeEntry();

            mediaBackupService.addDirectoryToZip(zip, MediaBackupService.DATABASE_PREFIX, dumpDir);
            mediaBackupService.addDirectoryToZip(zip, MediaBackupService.PICTURES_PREFIX, mediaBackupService.picturesDir());
            mediaBackupService.addDirectoryToZip(zip, MediaBackupService.VIDEOS_PREFIX, mediaBackupService.videosDir());
            mediaBackupService.addDirectoryToZip(zip, MediaBackupService.TONES_PREFIX, mediaBackupService.tonesDir());
        }
    }

    private void validateManifest(Path manifestFile) throws IOException {
        BackupManifest manifest;
        try {
            manifest = objectMapper.readValue(manifestFile.toFile(), BackupManifest.class);
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Backup manifest.json is not valid",
                    ex
            );
        }
        if (manifest.formatVersion() != BackupManifest.CURRENT_FORMAT_VERSION) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported backup format version " + manifest.formatVersion()
            );
        }
    }

    static String backupFilename() {
        String timestamp = FILENAME_TIME.withZone(MANILA).format(Instant.now());
        return "lpu-attendance-backup-" + timestamp + ".zip";
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            MediaBackupService.deleteRecursive(path);
        } catch (IOException ex) {
            log.warn("Could not delete temp path {}: {}", path, ex.toString());
        }
    }

    public record BackupDownload(String filename, StreamingResponseBody body) {
        public String contentDisposition() {
            return "attachment; filename=\"" + filename + "\"";
        }
    }
}
