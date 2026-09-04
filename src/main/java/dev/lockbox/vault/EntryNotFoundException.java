package dev.lockbox.vault;

public class EntryNotFoundException extends RuntimeException {

    public EntryNotFoundException(Long id) {
        super("Entry " + id + " does not exist or belongs to another user");
    }
}
