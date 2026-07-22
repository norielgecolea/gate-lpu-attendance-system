package org.nors.dev.codes.lpu.dto;

public record EmployeeImportResponse(
        int imported,
        int updated,
        int skippedDuplicates
) {
}
