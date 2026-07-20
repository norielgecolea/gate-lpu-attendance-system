package org.nors.dev.codes.lpu.dto;

import java.time.Instant;
import org.nors.dev.codes.lpu.model.TapErrorLog;

public record TapErrorLogResponse(
        String id,
        String identifier,
        String location,
        Instant tappedAt
) {
    public static TapErrorLogResponse from(TapErrorLog log) {
        return new TapErrorLogResponse(
                String.valueOf(log.getId()),
                log.getIdentifier(),
                log.getLocation(),
                log.getTappedAt()
        );
    }
}
