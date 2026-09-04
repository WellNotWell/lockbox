package dev.lockbox.vault;

public class AttachmentNotFoundException extends RuntimeException {

    public AttachmentNotFoundException(Long id) {
        super("Attachment " + id + " does not exist or belongs to another user");
    }
}
