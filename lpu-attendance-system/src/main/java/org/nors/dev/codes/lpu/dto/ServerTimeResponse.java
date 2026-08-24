package org.nors.dev.codes.lpu.dto;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public record ServerTimeResponse(
        Instant serverTime,
        String zoneId,
        String utcOffset
) {
    static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Manila");

    public static ServerTimeResponse systemNow() {
        ZonedDateTime zoned = ZonedDateTime.now(CAMPUS_ZONE);
        return new ServerTimeResponse(zoned.toInstant(), CAMPUS_ZONE.getId(), zoned.getOffset().getId());
    }
}
