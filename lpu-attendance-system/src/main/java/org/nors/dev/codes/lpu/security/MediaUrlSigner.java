package org.nors.dev.codes.lpu.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.nors.dev.codes.lpu.service.JwtService;
import org.springframework.stereotype.Component;

/**
 * Signs picture, video, and tone URLs so a leaked link stops working.
 * The expiry is long enough for a kiosk left open for weeks; the browser cache stays private.
 */
@Component
public class MediaUrlSigner {

    static final long MAX_AGE_SECONDS = 30L * 24 * 60 * 60;

    private static MediaUrlSigner instance;

    private final byte[] key;

    public MediaUrlSigner(JwtService jwtService) {
        this.key = jwtService.secretMaterial().getBytes(StandardCharsets.UTF_8);
        instance = this;
    }

    /** Signs a stored media path. Returns the path unchanged when the signer is not initialized. */
    public static String signStored(String storedPath) {
        MediaUrlSigner signer = instance;
        if (signer == null) {
            return storedPath;
        }
        return signer.sign(storedPath);
    }

    public String sign(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return storedPath;
        }
        String path = MediaPaths.stripToMediaPath(storedPath.trim());
        if (!MediaPaths.isMediaPath(path)) {
            return null;
        }
        long exp = Instant.now().getEpochSecond() + MAX_AGE_SECONDS;
        return path + "?exp=" + exp + "&sig=" + mac(path, exp);
    }

    public boolean isValid(String path, String expRaw, String sig) {
        if (!MediaPaths.isMediaPath(path) || expRaw == null || sig == null || sig.isBlank()) {
            return false;
        }
        long exp;
        try {
            exp = Long.parseLong(expRaw);
        } catch (NumberFormatException ex) {
            return false;
        }
        long now = Instant.now().getEpochSecond();
        if (exp < now - 30 || exp > now + MAX_AGE_SECONDS + 30) {
            return false;
        }
        return MessageDigest.isEqual(
                mac(path, exp).getBytes(StandardCharsets.UTF_8),
                sig.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String mac(String path, long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] raw = mac.doFinal((path + "\n" + exp).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign media URL", ex);
        }
    }
}
