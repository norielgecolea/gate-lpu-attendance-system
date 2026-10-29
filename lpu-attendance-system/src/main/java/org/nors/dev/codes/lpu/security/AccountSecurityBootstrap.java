package org.nors.dev.codes.lpu.security;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nors.dev.codes.lpu.model.Role;
import org.nors.dev.codes.lpu.model.User;
import org.nors.dev.codes.lpu.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first superadmin with a random password when the database has no users,
 * and flags any account that still uses a published seed password.
 */
@Component
public class AccountSecurityBootstrap implements ApplicationRunner {

    private static final Logger log = LogManager.getLogger(AccountSecurityBootstrap.class);
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Path secretsDir;

    public AccountSecurityBootstrap(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.security.secrets-dir:data/secrets}") String secretsDir
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.secretsDir = Path.of(secretsDir);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findAll().isEmpty()) {
            createInitialSuperadmin();
            return;
        }
        for (User user : userRepository.findAll()) {
            if (user.isPasswordChangeRequired()) {
                continue;
            }
            if (DefaultPasswords.matchesHash(passwordEncoder, user.getPasswordHash())) {
                user.setPasswordChangeRequired(true);
                user.setUpdatedAt(Instant.now());
                userRepository.save(user);
                log.warn("Account {} still uses a published seed password and must change it at next sign-in", user.getUsername());
            }
        }
    }

    private void createInitialSuperadmin() {
        String password = randomPassword();
        Path file = secretsDir.resolve("initial-admin-password");
        try {
            Files.createDirectories(secretsDir);
            Files.writeString(
                    file,
                    "username=superadmin" + System.lineSeparator() + "password=" + password + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            try {
                Files.setPosixFilePermissions(file, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            } catch (UnsupportedOperationException ignored) {
                // Windows doesn't support POSIX modes.
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to store the initial superadmin password at " + file, ex);
        }

        Instant now = Instant.now();
        User user = new User();
        user.setUsername("superadmin");
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(Role.SUPERADMIN);
        user.setActive(true);
        user.setPasswordChangeRequired(true);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.persist(user);
        log.warn("Created the first superadmin. The one-time password was written to {}", file);
    }

    private static String randomPassword() {
        StringBuilder password = new StringBuilder(24);
        for (int i = 0; i < 24; i++) {
            password.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return password.toString();
    }
}
