package org.nors.dev.codes.lpu.dto;

import java.util.List;

public record JdbcBackupMeta(
        String engine,
        int version,
        List<JdbcBackupTable> tables,
        List<JdbcBackupSequence> sequences
) {
    public static final String ENGINE = "jdbc";
    public static final int VERSION = 1;
}
