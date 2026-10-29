package org.nors.dev.codes.lpu.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Per-username login failure window. Shared campus NATs are not locked out together. */
@Component
public class LoginRateLimiter {

    static final int MAX_FAILURES = 8;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final ConcurrentHashMap<String, AttemptWindow> attempts = new ConcurrentHashMap<>();

    public void ensureAllowed(String username) {
        AttemptWindow window = attempts.get(key(username));
        if (window != null && window.isLocked(Instant.now())) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many login attempts. Try again later."
            );
        }
    }

    public void recordFailure(String username) {
        Instant now = Instant.now();
        attempts.compute(key(username), (ignored, existing) -> {
            AttemptWindow window = existing == null || existing.isExpired(now) ? new AttemptWindow() : existing;
            window.record(now);
            return window;
        });
        if (attempts.size() > 10_000) {
            attempts.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
        }
    }

    public void clear(String username) {
        attempts.remove(key(username));
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private static final class AttemptWindow {
        private int failures;
        private Instant windowStart = Instant.EPOCH;

        private synchronized void record(Instant now) {
            if (isExpired(now)) {
                failures = 0;
                windowStart = now;
            }
            failures++;
        }

        private synchronized boolean isLocked(Instant now) {
            return !isExpired(now) && failures >= MAX_FAILURES;
        }

        private synchronized boolean isExpired(Instant now) {
            return windowStart.plus(WINDOW).isBefore(now);
        }
    }
}
