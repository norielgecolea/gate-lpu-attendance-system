package org.nors.dev.codes.lpu.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;

public record TapRequest(
        @NotBlank(message = "ID or RFID is required") String identifier,
        /** Used only when a superadmin taps on behalf of a guard gate. */
        String location,
        /**
         * Superadmin kiosk only. {@code TIME_IN} or {@code TIME_OUT}.
         * Omit for a normal alternating gate tap.
         */
        String action,
        /**
         * Superadmin kiosk only. When set, the tap is stored at this instant
         * instead of the current time. Campus offset expected ({@code +08:00}).
         */
        OffsetDateTime tappedAt
) {
}
