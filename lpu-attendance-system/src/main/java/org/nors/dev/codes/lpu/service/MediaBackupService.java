package org.nors.dev.codes.lpu.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.nors.dev.codes.lpu.config.UploadProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MediaBackupService {

    static final String PICTURES_PREFIX = "pictures";
    static final String VIDEOS_PREFIX = "videos";
    static final String TONES_PREFIX = "tones";
    static final String MANIFEST_ENTRY = "manifest.json";
    static final String DATABASE_ENTRY = "database.sql";

    private final Path picturesDir;
    private final Path videosDir;
    private final Path tonesDir;

    public MediaBackupService(UploadProperties uploadProperties) {
        this.picturesDir = Paths.get(uploadProperties.getPicturesDir()).toAbsolutePath().normalize();
        this.videosDir = Paths.get(uploadProperties.getVideosDir()).toAbsolutePath().normalize();
        this.tonesDir = Paths.get(uploadProperties.getTonesDir()).toAbsolutePath().normalize();
    }

    public Path picturesDir() {
        return picturesDir;
    }

    public Path videosDir() {
        return videosDir;
    }

    public Path tonesDir() {
        return tonesDir;
    }

    public void addDirectoryToZip(ZipOutputStream zip, String prefix, Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.getFileName().toString().startsWith(".")) {
                    return FileVisitResult.CONTINUE;
                }
                Path relative = directory.relativize(file);
                String entryName = prefix + "/" + relative.toString().replace('\\', '/');
                zip.putNextEntry(new ZipEntry(entryName));
                try (InputStream input = Files.newInputStream(file)) {
                    input.transferTo(zip);
                }
                zip.closeEntry();
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public ExtractedBackup extractZip(Path zipFile, Path stagingRoot) throws IOException {
        Files.createDirectories(stagingRoot);
        Path sqlFile = null;
        Path manifestFile = null;
        int pictures = 0;
        int videos = 0;
        int tones = 0;

        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = normalizeEntryName(entry.getName());
                if (!isAllowedEntry(name)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Backup archive contains an unexpected path: " + name
                    );
                }
                Path destination = resolveSafeZipPath(stagingRoot, name);
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                    continue;
                }
                Files.createDirectories(destination.getParent());
                try (InputStream input = zip.getInputStream(entry); OutputStream output = Files.newOutputStream(destination)) {
                    input.transferTo(output);
                }
                if (DATABASE_ENTRY.equals(name)) {
                    sqlFile = destination;
                } else if (MANIFEST_ENTRY.equals(name)) {
                    manifestFile = destination;
                } else if (name.startsWith(PICTURES_PREFIX + "/")) {
                    pictures++;
                } else if (name.startsWith(VIDEOS_PREFIX + "/")) {
                    videos++;
                } else if (name.startsWith(TONES_PREFIX + "/")) {
                    tones++;
                }
            }
        } catch (ZipException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is not a valid zip backup", ex);
        }

        if (manifestFile == null || !Files.isRegularFile(manifestFile)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Not a valid LPU attendance backup (missing manifest.json)"
            );
        }
        if (sqlFile == null || !Files.isRegularFile(sqlFile) || Files.size(sqlFile) == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Not a valid LPU attendance backup (missing database.sql)"
            );
        }

        return new ExtractedBackup(
                sqlFile,
                manifestFile,
                stagingRoot.resolve(PICTURES_PREFIX),
                stagingRoot.resolve(VIDEOS_PREFIX),
                stagingRoot.resolve(TONES_PREFIX),
                pictures,
                videos,
                tones
        );
    }

    public int replaceDirectory(Path live, Path incoming) throws IOException {
        Files.createDirectories(incoming);
        if (live.getParent() != null) {
            Files.createDirectories(live.getParent());
        }

        Path backup = live.resolveSibling(live.getFileName() + ".restore-prev");
        deleteRecursive(backup);

        boolean liveMoved = false;
        try {
            if (Files.exists(live)) {
                moveDirectory(live, backup);
                liveMoved = true;
            }
            moveDirectory(incoming, live);
        } catch (IOException ex) {
            if (liveMoved && Files.exists(backup) && !Files.exists(live)) {
                moveDirectory(backup, live);
            }
            throw ex;
        }

        deleteRecursive(backup);
        return countFiles(live);
    }

    static void moveDirectory(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            copyDirectory(source, target);
            deleteRecursive(source);
        }
    }

    static void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static boolean isAllowedEntry(String name) {
        String normalized = normalizeEntryName(name);
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return MANIFEST_ENTRY.equals(normalized)
                || DATABASE_ENTRY.equals(normalized)
                || PICTURES_PREFIX.equals(normalized)
                || normalized.startsWith(PICTURES_PREFIX + "/")
                || VIDEOS_PREFIX.equals(normalized)
                || normalized.startsWith(VIDEOS_PREFIX + "/")
                || TONES_PREFIX.equals(normalized)
                || normalized.startsWith(TONES_PREFIX + "/");
    }

    static Path resolveSafeZipPath(Path root, String entryName) {
        String normalized = normalizeEntryName(entryName);
        if (normalized.isBlank() || normalized.startsWith("/") || normalized.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid backup path: " + entryName);
        }
        Path resolved = root.resolve(normalized).normalize();
        if (!resolved.startsWith(root)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid backup path: " + entryName);
        }
        return resolved;
    }

    static String normalizeEntryName(String name) {
        return name == null ? "" : name.replace('\\', '/');
    }

    static void deleteRecursive(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static int countFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        try (var stream = Files.walk(directory)) {
            return (int) stream.filter(Files::isRegularFile).count();
        }
    }

    record ExtractedBackup(
            Path sqlFile,
            Path manifestFile,
            Path picturesDir,
            Path videosDir,
            Path tonesDir,
            int picturesCopied,
            int videosCopied,
            int tonesCopied
    ) {
    }
}
