package org.nors.dev.codes.lpu.dto;

import java.time.Instant;
import org.nors.dev.codes.lpu.model.StudentAuditEvent;

public record StudentAuditEventResponse(
        String id,
        String action,
        Long actorUserId,
        String actorUsername,
        Instant createdAt
) {
    public static StudentAuditEventResponse from(StudentAuditEvent event) {
        return new StudentAuditEventResponse(
                String.valueOf(event.getId()),
                event.getAction(),
                event.getActorUserId(),
                event.getActorUsername(),
                event.getCreatedAt()
        );
    }
}
