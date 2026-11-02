package org.nors.dev.codes.lpu.security;

import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Retired seed passwords that were published with the database scripts. */
public final class DefaultPasswords {

    private static final Set<String> RETIRED = Set.of("SuperAdmin@123", "Guard@123");

    private DefaultPasswords() {
    }

    public static boolean isRetired(String password) {
        return password != null && RETIRED.contains(password);
    }

    public static boolean matchesHash(PasswordEncoder encoder, String passwordHash) {
        if (encoder == null || passwordHash == null || passwordHash.isBlank()) {
            return false;
        }
        for (String retired : RETIRED) {
            if (encoder.matches(retired, passwordHash)) {
                return true;
            }
        }
        return false;
    }
}
