package org.nors.dev.codes.lpu.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nors.dev.codes.lpu.config.UploadProperties;
import org.nors.dev.codes.lpu.dto.PhotoCleanupResponse;

class PhotoStorageServiceTest {

    @TempDir
    Path tempDir;

    private PhotoStorageService service;
    private Path picturesDir;

    @BeforeEach
    void setUp() {
        picturesDir = tempDir.resolve("pictures");
        UploadProperties properties = new UploadProperties();
        properties.setPicturesDir(picturesDir.toString());
        properties.setPhotoOptimizationEnabled(false);
        service = new PhotoStorageService(properties, new ImageOptimizationService(properties));
    }

    @Test
    void filenameFromPublicPath_rejectsTraversal() {
        assertNull(PhotoStorageService.filenameFromPublicPath("/pictures/../secret.jpg"));
        assertNull(PhotoStorageService.filenameFromPublicPath("/videos/clip.mp4"));
        assertEquals("uuid.jpg", PhotoStorageService.filenameFromPublicPath("/pictures/uuid.jpg"));
    }

    @Test
    void deleteIfReplaced_removesPreviousFile() throws Exception {
        Path oldFile = picturesDir.resolve("old.jpg");
        Path newFile = picturesDir.resolve("new.jpg");
        Files.writeString(oldFile, "old");
        Files.writeString(newFile, "new");

        service.deleteIfReplaced("/pictures/old.jpg", "/pictures/new.jpg");

        assertFalse(Files.exists(oldFile));
        assertTrue(Files.exists(newFile));
    }

    @Test
    void deleteIfReplaced_keepsFileWhenPathUnchanged() throws Exception {
        Path file = picturesDir.resolve("same.jpg");
        Files.writeString(file, "same");

        service.deleteIfReplaced("/pictures/same.jpg", "/pictures/same.jpg");

        assertTrue(Files.exists(file));
    }

    @Test
    void deleteUnused_keepsReferencedFiles() throws Exception {
        Path kept = picturesDir.resolve("kept.jpg");
        Path leftover = picturesDir.resolve("leftover.jpg");
        Files.writeString(kept, "kept");
        Files.writeString(leftover, "leftover");

        PhotoCleanupResponse preview = service.inspectUnused(Set.of("/pictures/kept.jpg"));
        assertEquals(1, preview.referenced());
        assertEquals(2, preview.onDisk());
        assertEquals(1, preview.unused());

        PhotoCleanupResponse result = service.deleteUnused(Set.of("/pictures/kept.jpg"));
        assertEquals(1, result.deleted());
        assertEquals(0, result.unused());
        assertEquals(1, result.onDisk());
        assertTrue(Files.exists(kept));
        assertFalse(Files.exists(leftover));
    }
}
