package dev.lockbox.vault;

public class AttachmentTooLargeException extends RuntimeException {

    public AttachmentTooLargeException(long sizeBytes, long limitBytes) {
        super("Attachment of " + sizeBytes + " bytes exceeds the limit of " + limitBytes + " bytes");
    }
}
