package org.nors.dev.codes.lpu.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.nors.dev.codes.lpu.dto.ServerTimeResponse;

class KioskTimeControllerTest {

    @Test
    void time_returnsHostSystemClockAndZone() {
        ServerTimeResponse body = new KioskTimeController().time().getBody();

        assertNotNull(body);
        assertEquals(ZoneId.systemDefault().getId(), body.zoneId());
        assertNotNull(body.utcOffset());
        assertTrue(Duration.between(body.serverTime(), Instant.now()).abs().toMillis() < 1_000);
    }
}
