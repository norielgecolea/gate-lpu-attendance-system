package org.nors.dev.codes.lpu.security;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Refuses the signing key that used to ship in the repo. When no private key is configured,
 * a random key is created once and reused from a local file so restarts do not log everyone out.
 */
public final class JwtSecretResolver {

    public static final String PUBLISHED_DEFAULT = "bHB1LWdhdGUtYXR0ZW5kYW5jZS1qd3Qtc2VjcmV0LWtleS0zMmJ5dGVzIQ==";

    private static final Logger log = LogManager.getLogger(JwtSecretResolver.class);
    private static final String PUBLISHED_PLAINTEXT = "lpu-gate-attendance-jwt-secret-key-32bytes!";
    private static final SecureRandom RANDOM = new SecureRandom();

    public String resolve(String configured, Path secretFile) {
        if (isPrivate(configured)) {
            return configured.trim();
        }
        if (configured != null && !configured.isBlank()) {
            log.warn("Ignoring the published JWT signing key. A private key file will be used instead.");
        }
        return loadOrCreate(secretFile);
    }

    static boolean isPrivate(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String trimmed = value.trim();
        return !PUBLISHED_DEFAULT.equals(trimmed) && !PUBLISHED_PLAINTEXT.equals(trimmed);
    }

    private static String loadOrCreate(Path secretFile) {
        try {
            if (secretFile.getParent() != null) {
                Files.createDirectories(secretFile.getParent());
            }
            if (Files.isRegularFile(secretFile)) {
                String existing = Files.readString(secretFile).trim();
                if (isPrivate(existing)) {
                    return existing;
                }
                log.warn("Replacing a published JWT signing key stored at {}", secretFile);
            }
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            String created = Base64.getEncoder().encodeToString(bytes);
            Files.writeString(
                    secretFile,
                    created + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            restrictToOwner(secretFile);
            log.info("Generated a private JWT signing key at {}", secretFile);
            return created;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to load or create the JWT signing key at " + secretFile, ex);
        }
    }

    private static void restrictToOwner(Path secretFile) {
        try {
            Set<PosixFilePermission> ownerOnly = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(secretFile, ownerOnly);
        } catch (UnsupportedOperationException ex) {
            // Windows doesn't support POSIX modes.
        } catch (Exception ex) {
            log.warn("Could not restrict permissions on {}", secretFile);
        }
    }
}
