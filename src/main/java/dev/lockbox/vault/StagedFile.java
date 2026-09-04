package dev.lockbox.vault;

import javax.crypto.SecretKey;

public record StagedFile(String stagingKey, String fileName, String contentType, long sizeBytes,
                         SecretKey fileKey) {
}
