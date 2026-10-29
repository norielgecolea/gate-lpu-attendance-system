package org.nors.dev.codes.lpu.security;

import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Local picture, video, and tone paths. Remote and data URLs are rejected. */
public final class MediaPaths {

    private static final Pattern STORED = Pattern.compile(
            "^/(pictures|videos|tones)/[A-Za-z0-9][A-Za-z0-9._-]{0,200}$"
    );

    private MediaPaths() {
    }

    public static boolean isMediaPath(String path) {
        return path != null && STORED.matcher(path).matches();
    }

    /**
     * Accepts a stored path or a previously signed URL and returns the stored path.
     * Blank input stays blank. Anything else is rejected.
     */
    public static String storedPhoto(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String path = stripToMediaPath(raw.trim());
        if (!isMediaPath(path) || !path.startsWith("/pictures/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Photo must be an uploaded picture");
        }
        return path;
    }

    public static void setPhotoIfPresent(String raw, Consumer<String> setter) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        setter.accept(storedPhoto(raw));
    }

    static String stripToMediaPath(String raw) {
        String value = raw;
        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        if (value.startsWith("http://")
                || value.startsWith("https://")
                || value.startsWith("data:")
                || value.startsWith("//")) {
            return value;
        }
        int pictures = value.indexOf("/pictures/");
        int videos = value.indexOf("/videos/");
        int tones = value.indexOf("/tones/");
        int start = firstNonNegative(pictures, videos, tones);
        if (start >= 0) {
            value = value.substring(start);
        }
        return value;
    }

    private static int firstNonNegative(int... indexes) {
        int best = -1;
        for (int index : indexes) {
            if (index >= 0 && (best < 0 || index < best)) {
                best = index;
            }
        }
        return best;
    }
}
