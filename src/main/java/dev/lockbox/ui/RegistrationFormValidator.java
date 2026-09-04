package dev.lockbox.ui;

final class RegistrationFormValidator {

    static final int MIN_PASSWORD_LENGTH = 12;

    static final String USERNAME_EMPTY = "register.error.usernameEmpty";
    static final String PASSWORD_TOO_SHORT = "register.error.passwordTooShort";
    static final String PASSWORDS_DO_NOT_MATCH = "register.error.passwordsDoNotMatch";

    private RegistrationFormValidator() {
    }

    static ValidationResult validate(String username, String password, String confirmation) {
        String name = username == null ? "" : username.trim();
        String secret = password == null ? "" : password;
        String repeated = confirmation == null ? "" : confirmation;

        return new ValidationResult(
                name.isEmpty() ? USERNAME_EMPTY : null,
                secret.length() < MIN_PASSWORD_LENGTH ? PASSWORD_TOO_SHORT : null,
                !secret.isEmpty() && !secret.equals(repeated) ? PASSWORDS_DO_NOT_MATCH : null);
    }

    record ValidationResult(String usernameError, String passwordError, String confirmationError) {

        boolean valid() {
            return usernameError == null && passwordError == null && confirmationError == null;
        }
    }
}
