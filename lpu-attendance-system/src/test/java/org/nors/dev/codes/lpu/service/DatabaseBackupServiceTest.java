package org.nors.dev.codes.lpu.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DatabaseBackupServiceTest {

    @Test
    void quoteIdent_wrapsAndEscapes() {
        assertEquals("\"users\"", DatabaseBackupService.quoteIdent("users"));
        assertEquals("\"weird\"\"name\"", DatabaseBackupService.quoteIdent("weird\"name"));
    }
}
