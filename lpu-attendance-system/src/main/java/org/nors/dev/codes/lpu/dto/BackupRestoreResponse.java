package org.nors.dev.codes.lpu.dto;

public record BackupRestoreResponse(
        boolean databaseRestored,
        int picturesCopied,
        int videosCopied,
        int tonesCopied
) {
    public int filesCopied() {
        return picturesCopied + videosCopied + tonesCopied;
    }
}
