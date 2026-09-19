package org.nors.dev.codes.lpu.cluster;

import org.nors.dev.codes.lpu.model.KioskGroup;

public record PresenceRecord(
        String sessionId,
        String label,
        KioskGroup group,
        Integer pingMs,
        long lastPongEpochMs
) {
}
