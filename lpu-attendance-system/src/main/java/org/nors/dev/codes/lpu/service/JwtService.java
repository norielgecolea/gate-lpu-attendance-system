package org.nors.dev.codes.lpu.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Date;
import javax.crypto.SecretKey;
import org.nors.dev.codes.lpu.config.JwtProperties;
import org.nors.dev.codes.lpu.model.Role;
import org.nors.dev.codes.lpu.model.User;
import org.nors.dev.codes.lpu.security.JwtSecretResolver;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtProperties jwtProperties;
    private final String secretMaterial;
    private final SecretKey secretKey;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretMaterial = new JwtSecretResolver().resolve(
                jwtProperties.getSecret(),
                Path.of(jwtProperties.getSecretFile())
        );
        this.secretKey = buildKey(secretMaterial);
    }

    public String secretMaterial() {
        return secretMaterial;
    }

    private static SecretKey buildKey(String secret) {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (Exception ex) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        return Keys.hmacShaKeyFor(padIfNeeded(keyBytes));
    }

    private static byte[] padIfNeeded(byte[] keyBytes) {
        if (keyBytes.length >= 32) {
            return keyBytes;
        }
        byte[] padded = new byte[32];
        System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
        return padded;
    }

    public String generateToken(User user) {
        return generateToken(user, false);
    }

    public String generateToken(User user, boolean rememberMe) {
        long now = System.currentTimeMillis();
        long expirationMs = rememberMe
                ? jwtProperties.getRememberExpirationMs()
                : jwtProperties.getExpirationMs();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("role", user.getRole().name())
                .claim("uid", user.getId())
                .claim("location", user.getLocation() != null ? user.getLocation() : "")
                .claim("sv", user.getSessionVersion())
                .claim("rm", rememberMe)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(secretKey)
                .compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public Role extractRole(String token) {
        String role = parseClaims(token).get("role", String.class);
        return Role.valueOf(role);
    }

    public Long extractUserId(String token) {
        Object uid = parseClaims(token).get("uid");
        if (uid instanceof Number number) {
            return number.longValue();
        }
        if (uid instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        return null;
    }

    public String extractLocation(String token) {
        String location = parseClaims(token).get("location", String.class);
        return location == null || location.isBlank() ? null : location;
    }

    /** Missing claim means a token issued before session versions existed. */
    public int extractSessionVersion(String token) {
        Object version = parseClaims(token).get("sv");
        if (version instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    public boolean isRememberMe(String token) {
        Claims claims = parseClaims(token);
        Boolean remember = claims.get("rm", Boolean.class);
        if (remember != null) {
            return remember;
        }
        Date issuedAt = claims.getIssuedAt();
        Date expiration = claims.getExpiration();
        if (issuedAt == null || expiration == null) {
            return false;
        }
        return expiration.getTime() - issuedAt.getTime() > 86_400_000L;
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = parseClaims(token);
            return claims.getExpiration().after(new Date());
        } catch (Exception ex) {
            return false;
        }
    }

    public long getExpirationMs() {
        return jwtProperties.getExpirationMs();
    }

    public long getExpirationMs(boolean rememberMe) {
        return rememberMe
                ? jwtProperties.getRememberExpirationMs()
                : jwtProperties.getExpirationMs();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
