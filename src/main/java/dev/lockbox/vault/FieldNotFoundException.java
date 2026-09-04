package dev.lockbox.vault;

public class FieldNotFoundException extends RuntimeException {

    public FieldNotFoundException(Long fieldId) {
        super("Field %d does not exist or belongs to another user".formatted(fieldId));
    }
}
