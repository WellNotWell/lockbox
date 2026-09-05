package dev.lockbox.user;

public class PasswordAlreadyUsedException extends RuntimeException {

    public PasswordAlreadyUsedException() {
        super("This master password was already used before");
    }
}
