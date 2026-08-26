package org.nors.dev.codes.lpu.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DatabaseBackupServiceTest {

    @Test
    void parseJdbcUrl_readsHostPortAndDatabase() {
        DatabaseBackupService.PostgresTarget target = DatabaseBackupService.parseJdbcUrl(
                "jdbc:postgresql://postgres:5432/attendance",
                "postgres",
                "secret"
        );
        assertEquals("postgres", target.host());
        assertEquals("5432", target.port());
        assertEquals("attendance", target.database());
        assertEquals("postgres", target.username());
        assertEquals("secret", target.password());
    }

    @Test
    void parseJdbcUrl_defaultsPortAndStripsQuery() {
        DatabaseBackupService.PostgresTarget target = DatabaseBackupService.parseJdbcUrl(
                "jdbc:postgresql://localhost/postgres?ssl=true",
                "user",
                ""
        );
        assertEquals("localhost", target.host());
        assertEquals("5432", target.port());
        assertEquals("postgres", target.database());
    }

    @Test
    void parseJdbcUrl_rejectsNonPostgres() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DatabaseBackupService.parseJdbcUrl("jdbc:mysql://localhost:3306/db", "u", "p")
        );
    }
}
