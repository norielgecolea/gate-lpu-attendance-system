package org.nors.dev.codes.lpu.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.nors.dev.codes.lpu.config.JwtProperties;
import org.nors.dev.codes.lpu.service.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class SecurityControlsTest {

    @Test
    void publishedJwtSecretIsReplacedAndThenReused() throws Exception {
        Path dir = Files.createTempDirectory("jwt-secret-test");
        Path file = dir.resolve("jwt-secret");
        JwtSecretResolver resolver = new JwtSecretResolver();

        String first = resolver.resolve(JwtSecretResolver.PUBLISHED_DEFAULT, file);
        String second = resolver.resolve("", file);

        assertNotEquals(JwtSecretResolver.PUBLISHED_DEFAULT, first);
        assertEquals(first, second);
        assertTrue(JwtSecretResolver.isPrivate(first));
    }

    @Test
    void mediaSignatureRoundTripsAndRejectsTampering() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-only-private-signing-key-32bytes");
        properties.setSecretFile("unused-secret-file");
        MediaUrlSigner signer = new MediaUrlSigner(new JwtService(properties));

        String signed = signer.sign("/pictures/abc123.jpg");
        String path = signed.substring(0, signed.indexOf('?'));
        String query = signed.substring(signed.indexOf('?') + 1);
        String exp = query.substring(query.indexOf("exp=") + 4, query.indexOf("&sig="));
        String sig = query.substring(query.indexOf("sig=") + 4);

        assertEquals("/pictures/abc123.jpg", path);
        assertTrue(signer.isValid(path, exp, sig));
        assertFalse(signer.isValid(path, exp, sig + "x"));
        assertFalse(signer.isValid("/pictures/other.jpg", exp, sig));
        assertNull(signer.sign("https://evil.example/pictures/abc123.jpg"));
    }

    @Test
    void storedPhotoKeepsSignedPicturePathAndRejectsRemoteUrls() {
        assertEquals(
                "/pictures/abc123.jpg",
                MediaPaths.storedPhoto("/attendance-system/pictures/abc123.jpg?exp=1&sig=2")
        );
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> MediaPaths.storedPhoto("https://evil.example/track.jpg")
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void csvNeutralizesFormulaCells() {
        assertEquals("Ana", CsvCells.cell("Ana"));
        assertEquals("\"'=1+1\"", CsvCells.cell("=1+1"));
        assertEquals("\"Ana, Cruz\"", CsvCells.cell("Ana, Cruz"));
    }

    @Test
    void loginRateLimiterLocksAfterRepeatedFailures() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES; i++) {
            limiter.ensureAllowed("guard");
            limiter.recordFailure("guard");
        }
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> limiter.ensureAllowed("Guard")
        );
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
        limiter.clear("guard");
        limiter.ensureAllowed("guard");
    }
}
