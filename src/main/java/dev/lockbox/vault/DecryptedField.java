package dev.lockbox.vault;

public record DecryptedField(Long id, String label, String value, boolean secret) {
}
