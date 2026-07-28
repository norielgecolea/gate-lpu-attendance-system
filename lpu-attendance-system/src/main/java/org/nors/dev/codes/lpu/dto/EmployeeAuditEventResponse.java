package org.nors.dev.codes.lpu.dto;

import java.time.Instant;
import org.nors.dev.codes.lpu.model.EmployeeAuditEvent;

public record EmployeeAuditEventResponse(
        String id,
        String action,
        Long actorUserId,
        String actorUsername,
        Instant createdAt
) {
    public static EmployeeAuditEventResponse from(EmployeeAuditEvent event) {
        return new EmployeeAuditEventResponse(
                String.valueOf(event.getId()),
                event.getAction(),
                event.getActorUserId(),
                event.getActorUsername(),
                event.getCreatedAt()
        );
    }
}
