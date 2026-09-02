package org.nors.dev.codes.lpu.dto;

public record PhotoCleanupResponse(
        int referenced,
        int onDisk,
        int unused,
        int deleted,
        int failed
) {
}
