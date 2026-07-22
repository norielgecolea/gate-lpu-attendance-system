package org.nors.dev.codes.lpu.dto;

import java.time.Instant;
import org.nors.dev.codes.lpu.model.GateTone;

public record GateToneResponse(
        String id,
        String url,
        String originalName,
        String contentType,
        long sizeBytes,
        Instant uploadedAt
) {
    public static GateToneResponse from(GateTone tone) {
        return new GateToneResponse(
                String.valueOf(tone.getId()),
                tone.getFilePath(),
                tone.getOriginalName(),
                tone.getContentType(),
                tone.getSizeBytes(),
                tone.getUploadedAt()
        );
    }
}
