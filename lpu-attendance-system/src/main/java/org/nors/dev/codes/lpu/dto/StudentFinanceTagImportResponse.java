package org.nors.dev.codes.lpu.dto;

public record StudentFinanceTagImportResponse(
        int tagged,
        int alreadyTagged,
        int notFound
) {
}
