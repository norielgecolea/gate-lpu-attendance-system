package org.nors.dev.codes.lpu.dto;

public record StudentImportResponse(
        int imported,
        int skippedDuplicates
) {
}
