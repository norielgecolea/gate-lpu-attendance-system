package org.nors.dev.codes.lpu.dto;

public record JdbcBackupSequence(
        String name,
        long lastValue,
        boolean isCalled
) {
}
