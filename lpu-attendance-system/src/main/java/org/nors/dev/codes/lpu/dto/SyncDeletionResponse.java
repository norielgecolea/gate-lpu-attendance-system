package org.nors.dev.codes.lpu.dto;

import java.time.Instant;
import org.nors.dev.codes.lpu.model.SyncDeletionTombstone;

public record SyncDeletionResponse(
        String personType,
        Long sourceId,
        String personNo,
        Instant deletedAt
) {
    public static SyncDeletionResponse from(SyncDeletionTombstone tombstone) {
        return new SyncDeletionResponse(
                tombstone.getPersonType(),
                tombstone.getPersonId(),
                tombstone.getPersonNo(),
                tombstone.getDeletedAt()
        );
    }
}
