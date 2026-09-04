package dev.lockbox.vault;

public record DecryptedField(Long id, FieldKind kind, String label, boolean secret, String value, FileInfo file) {

    public boolean isFile() {
        return kind == FieldKind.FILE;
    }
}
