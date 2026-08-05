package org.nors.dev.codes.lpu.dto;

import java.util.List;

public record SyncPageResponse<T>(
        List<T> records,
        String nextCursor,
        boolean hasMore
) {
}
