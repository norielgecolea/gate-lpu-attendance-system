package org.nors.dev.codes.lpu.dto;

import java.util.List;
import java.util.Map;

public record GateToneSettingsResponse(
        List<GateToneResponse> tones,
        /** Event type → tone id, or null when using the built-in default. */
        Map<String, String> assignments
) {
}
