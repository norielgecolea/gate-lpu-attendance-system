package org.nors.dev.codes.lpu.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nors.dev.codes.lpu.config.UploadProperties;
import org.springframework.web.server.ResponseStatusException;

class MediaBackupServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void isAllowedEntry_acceptsBackupLayout() {
        assertTrue(MediaBackupService.isAllowedEntry("manifest.json"));
        assertTrue(MediaBackupService.isAllowedEntry("database/meta.json"));
        assertTrue(MediaBackupService.isAllowedEntry("database/users.csv"));
        assertTrue(MediaBackupService.isAllowedEntry("pictures/"));
        assertTrue(MediaBackupService.isAllowedEntry("pictures/uuid.jpg"));
        assertTrue(MediaBackupService.isAllowedEntry("videos/clip.mp4"));
        assertTrue(MediaBackupService.isAllowedEntry("tones/beep.mp3"));
    }

    @Test
    void isAllowedEntry_rejectsUnknownPaths() {
        assertFalse(MediaBackupService.isAllowedEntry("etc/passwd"));
        assertFalse(MediaBackupService.isAllowedEntry("secret.txt"));
        assertFalse(MediaBackupService.isAllowedEntry("pictures-extra/file.jpg"));
    }

    @Test
    void resolveSafeZipPath_blocksPathTraversal() {
        assertThrows(
                ResponseStatusException.class,
                () -> MediaBackupService.resolveSafeZipPath(tempDir, "../outside.txt")
        );
        assertThrows(
                ResponseStatusException.class,
                () -> MediaBackupService.resolveSafeZipPath(tempDir, "/etc/passwd")
        );
        Path resolved = MediaBackupService.resolveSafeZipPath(tempDir, "pictures/uuid.jpg");
        assertTrue(resolved.startsWith(tempDir));
        assertEquals("uuid.jpg", resolved.getFileName().toString());
    }

    @Test
    void extractZip_rejectsTraversingPicturePath() throws IOException {
        Path zipPath = tempDir.resolve("bad.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zip.putNextEntry(new ZipEntry("pictures/../../outside.txt"));
            zip.write("nope".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        MediaBackupService service = new MediaBackupService(uploadProperties());
        assertThrows(
                ResponseStatusException.class,
                () -> service.extractZip(zipPath, tempDir.resolve("staging"))
        );
    }

    @Test
    void extractZip_readsValidArchive() throws IOException {
        Path zipPath = tempDir.resolve("good.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write("{\"formatVersion\":1}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("database/meta.json"));
            zip.write("{\"engine\":\"jdbc\",\"version\":1}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("database/users.csv"));
            zip.write("1,admin\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("pictures/photo.jpg"));
            zip.write("img".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        MediaBackupService service = new MediaBackupService(uploadProperties());
        MediaBackupService.ExtractedBackup extracted = service.extractZip(zipPath, tempDir.resolve("staging"));
        assertEquals(1, extracted.picturesCopied());
        assertEquals("{\"engine\":\"jdbc\",\"version\":1}", Files.readString(extracted.databaseDir().resolve("meta.json")));
    }

    @Test
    void replaceDirectory_keepsLiveFolderAndSwapsFiles() throws IOException {
        Path live = tempDir.resolve("live-pictures");
        Path incoming = tempDir.resolve("incoming-pictures");
        Files.createDirectories(live);
        Files.createDirectories(incoming);
        Files.writeString(live.resolve("old.jpg"), "old");
        Files.writeString(incoming.resolve("new.jpg"), "new");

        MediaBackupService service = new MediaBackupService(uploadProperties());
        int copied = service.replaceDirectory(live, incoming);

        assertTrue(Files.isDirectory(live));
        assertEquals(1, copied);
        assertFalse(Files.exists(live.resolve("old.jpg")));
        assertEquals("new", Files.readString(live.resolve("new.jpg")));
    }

    private UploadProperties uploadProperties() {
        UploadProperties properties = new UploadProperties();
        properties.setPicturesDir(tempDir.resolve("pictures").toString());
        properties.setVideosDir(tempDir.resolve("videos").toString());
        properties.setTonesDir(tempDir.resolve("tones").toString());
        return properties;
    }
}
