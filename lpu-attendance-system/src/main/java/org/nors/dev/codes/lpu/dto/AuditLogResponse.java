package org.nors.dev.codes.lpu.dto;

import java.time.Instant;

public record AuditLogResponse(
        String id,
        String personType,
        String personId,
        String personName,
        String personNo,
        String action,
        Long actorUserId,
        String actorUsername,
        Instant createdAt
) {
}
