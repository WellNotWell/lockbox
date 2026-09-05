package dev.lockbox.backup;

import java.util.List;

public final class BackupFormat {

    public static final int VERSION = 2;
    public static final String MANIFEST = "vault.json";
    public static final String FILES = "files/";

    private BackupFormat() {
    }

    public record Manifest(int version, String username, String createdAt, String salt,
                           List<BackupEntry> entries) {
    }

    public record BackupEntry(String title, String dataKey, String createdAt, String updatedAt,
                              List<BackupField> fields) {
    }

    public record BackupField(String kind, String label, boolean secret, int sortOrder, String value,
                              String fileName, String contentType, Long sizeBytes, String fileKey,
                              String fileInArchive) {
    }
}
