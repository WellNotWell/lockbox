package dev.lockbox.ui;

import dev.lockbox.ui.RegistrationFormValidator.ValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationFormValidatorTest {

    private static final String GOOD_PASSWORD = "correct horse battery";

    @Test
    @DisplayName("Complete and consistent form passes")
    void acceptsValidForm() {
        ValidationResult result = RegistrationFormValidator.validate("lesya", GOOD_PASSWORD, GOOD_PASSWORD);

        assertThat(result.valid()).isTrue();
        assertThat(result.usernameError()).isNull();
    }

    @Test
    @DisplayName("Empty user name is reported on the user name field")
    void reportsEmptyUserName() {
        ValidationResult result = RegistrationFormValidator.validate("   ", GOOD_PASSWORD, GOOD_PASSWORD);

        assertThat(result.valid()).isFalse();
        assertThat(result.usernameError()).isEqualTo(RegistrationFormValidator.USERNAME_EMPTY);
        assertThat(result.passwordError()).isNull();
    }

    @Test
    @DisplayName("Validator returns message keys, not texts, so it does not depend on the language")
    void reportsShortPassword() {
        ValidationResult result = RegistrationFormValidator.validate("lesya", "short", "short");

        assertThat(result.passwordError()).isEqualTo(RegistrationFormValidator.PASSWORD_TOO_SHORT);
        assertThat(result.confirmationError()).isNull();
    }

    @Test
    @DisplayName("Mismatched repetition is reported on the second password field")
    void reportsMismatchedPasswords() {
        ValidationResult result = RegistrationFormValidator.validate("lesya", GOOD_PASSWORD, "another password");

        assertThat(result.confirmationError()).isEqualTo(RegistrationFormValidator.PASSWORDS_DO_NOT_MATCH);
        assertThat(result.passwordError()).isNull();
    }

    @Test
    @DisplayName("Empty password is reported once, not twice")
    void reportsEmptyPasswordOnlyOnce() {
        ValidationResult result = RegistrationFormValidator.validate("lesya", "", "");

        assertThat(result.passwordError()).isNotNull();
        assertThat(result.confirmationError()).isNull();
    }

    @Test
    @DisplayName("Several problems are reported together")
    void reportsAllProblemsAtOnce() {
        ValidationResult result = RegistrationFormValidator.validate("", "short", "other");

        assertThat(result.usernameError()).isNotNull();
        assertThat(result.passwordError()).isNotNull();
        assertThat(result.confirmationError()).isNotNull();
    }

    @Test
    @DisplayName("Null values are treated as empty input")
    void handlesNullValues() {
        ValidationResult result = RegistrationFormValidator.validate(null, null, null);

        assertThat(result.valid()).isFalse();
        assertThat(result.usernameError()).isNotNull();
    }
}
