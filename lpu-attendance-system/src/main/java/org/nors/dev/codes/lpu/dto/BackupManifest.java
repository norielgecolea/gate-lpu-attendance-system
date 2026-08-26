package org.nors.dev.codes.lpu.dto;

import java.util.List;

public record BackupManifest(
        int formatVersion,
        String createdAt,
        String appVersion,
        List<String> included
) {
    public static final int CURRENT_FORMAT_VERSION = 1;
}
