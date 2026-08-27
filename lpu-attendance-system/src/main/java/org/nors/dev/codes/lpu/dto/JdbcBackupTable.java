package org.nors.dev.codes.lpu.dto;

import java.util.List;

public record JdbcBackupTable(
        String name,
        List<String> columns,
        String file
) {
}
