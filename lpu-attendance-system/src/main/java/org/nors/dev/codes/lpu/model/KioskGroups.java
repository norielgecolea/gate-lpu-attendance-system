package org.nors.dev.codes.lpu.model;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Maps roles onto kiosk groups and enforces who may query which venue. */
public final class KioskGroups {

    private KioskGroups() {
    }

    public static KioskGroup fromRole(Role role) {
        if (role == null) {
            return KioskGroup.MAIN_GATES;
        }
        return switch (role) {
            case LIBRARIAN, LIBRARY_KIOSK -> KioskGroup.LIBRARY;
            case OLIVE, OLIVE_KIOSK -> KioskGroup.OLIVE_HOTEL;
            default -> KioskGroup.MAIN_GATES;
        };
    }

    public static boolean isKioskRole(Role role) {
        return role == Role.GUARD || role == Role.LIBRARY_KIOSK || role == Role.OLIVE_KIOSK;
    }

    public static boolean isVenueAdmin(Role role) {
        return role == Role.LIBRARIAN || role == Role.OLIVE;
    }

    /**
     * List, dashboard, recap, and recent feeds. Librarian/Olive are locked to their
     * venue. Superadmin/OSAS/HR/Monitoring may pick a group; omitted defaults to Main Gates.
     */
    public static KioskGroup resolveForView(Role role, String requested) {
        KioskGroup locked = lockedGroup(role);
        if (locked != null) {
            return locked;
        }
        if (role == Role.SUPERADMIN || role == Role.OSAS || role == Role.HR || role == Role.MONITORING) {
            return parseOrDefault(requested, KioskGroup.MAIN_GATES);
        }
        return KioskGroup.MAIN_GATES;
    }

    /**
     * CSV export. Superadmin/OSAS/HR may pick any venue; Librarian/Olive stay locked.
     */
    public static KioskGroup resolveForExport(Role role, String requested) {
        KioskGroup locked = lockedGroup(role);
        if (locked != null) {
            return locked;
        }
        if (role == Role.SUPERADMIN || role == Role.OSAS || role == Role.HR) {
            return parseOrDefault(requested, KioskGroup.MAIN_GATES);
        }
        return KioskGroup.MAIN_GATES;
    }

    public static KioskGroup parseOrDefault(String raw, KioskGroup fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return KioskGroup.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "kioskGroup must be MAIN_GATES, LIBRARY, or OLIVE_HOTEL"
            );
        }
    }

    public static String slug(KioskGroup group) {
        return switch (group) {
            case MAIN_GATES -> "main-gates";
            case LIBRARY -> "library";
            case OLIVE_HOTEL -> "olive-hotel";
        };
    }

    private static KioskGroup lockedGroup(Role role) {
        if (role == Role.LIBRARIAN || role == Role.LIBRARY_KIOSK) {
            return KioskGroup.LIBRARY;
        }
        if (role == Role.OLIVE || role == Role.OLIVE_KIOSK) {
            return KioskGroup.OLIVE_HOTEL;
        }
        return null;
    }
}
