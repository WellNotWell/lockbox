package dev.lockbox.vault;

public record StoredFile(String fileName, String contentType, byte[] content) {
}
