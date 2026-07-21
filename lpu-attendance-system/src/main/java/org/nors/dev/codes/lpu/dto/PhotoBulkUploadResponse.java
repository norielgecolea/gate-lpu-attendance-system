package org.nors.dev.codes.lpu.dto;

public record PhotoBulkUploadResponse(
        int updated,
        int notFound,
        int skippedInvalid
) {
}
