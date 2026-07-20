package org.nors.dev.codes.lpu.dto;

import java.time.Instant;
import org.nors.dev.codes.lpu.model.GuardVideo;

public record GuardVideoResponse(
        String id,
        String url,
        String originalName,
        String contentType,
        long sizeBytes,
        Instant uploadedAt
) {
    public static GuardVideoResponse from(GuardVideo video) {
        return new GuardVideoResponse(
                String.valueOf(video.getId()),
                video.getFilePath(),
                video.getOriginalName(),
                video.getContentType(),
                video.getSizeBytes(),
                video.getUploadedAt()
        );
    }
}
