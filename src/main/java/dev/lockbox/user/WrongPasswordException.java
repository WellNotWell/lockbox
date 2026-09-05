package dev.lockbox.user;

public class WrongPasswordException extends RuntimeException {

    public WrongPasswordException() {
        super("The current master password does not match");
    }
}
