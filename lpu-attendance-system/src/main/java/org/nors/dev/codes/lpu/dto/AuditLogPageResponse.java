package org.nors.dev.codes.lpu.dto;

import java.util.List;

public record AuditLogPageResponse(
        List<AuditLogResponse> items,
        long total
) {
}
