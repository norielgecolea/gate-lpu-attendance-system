package org.nors.dev.codes.lpu.dto;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public record ServerTimeResponse(
        Instant serverTime,
        String zoneId,
        String utcOffset
) {
    public static ServerTimeResponse systemNow() {
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime zoned = ZonedDateTime.now(zone);
        return new ServerTimeResponse(zoned.toInstant(), zone.getId(), zoned.getOffset().getId());
    }
}
