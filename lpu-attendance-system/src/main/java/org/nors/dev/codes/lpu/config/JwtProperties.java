package org.nors.dev.codes.lpu.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private String secret;
    /** Used when {@code secret} is blank or is the key that used to ship in the repo. */
    private String secretFile = "data/secrets/jwt-secret";
    /** Default session length when "Remember me" is off (8 hours). */
    private long expirationMs = 28_800_000L;
    /**
     * Session length when "Remember me" is on. Defaults to ~10 years so the
     * session effectively lasts until the user explicitly logs out.
     */
    private long rememberExpirationMs = 315_360_000_000L;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getSecretFile() {
        return secretFile;
    }

    public void setSecretFile(String secretFile) {
        this.secretFile = secretFile;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }

    public long getRememberExpirationMs() {
        return rememberExpirationMs;
    }

    public void setRememberExpirationMs(long rememberExpirationMs) {
        this.rememberExpirationMs = rememberExpirationMs;
    }
}
