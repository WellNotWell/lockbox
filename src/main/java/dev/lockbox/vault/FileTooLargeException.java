package dev.lockbox.vault;

public class FileTooLargeException extends RuntimeException {

    public FileTooLargeException(long sizeBytes, long limitBytes) {
        super("File of " + sizeBytes + " bytes exceeds the limit of " + limitBytes + " bytes");
    }
}
