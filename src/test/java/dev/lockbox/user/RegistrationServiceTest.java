package dev.lockbox.user;

import dev.lockbox.crypto.CryptoProperties;
import dev.lockbox.crypto.KeyDerivation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistrationServiceTest {

    private final UserRepository repository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final KeyDerivation keyDerivation = new KeyDerivation(new CryptoProperties(65536, 3, 1, 16));
    private final RegistrationService service =
            new RegistrationService(repository, passwordEncoder, keyDerivation);

    @Test
    @DisplayName("Password is stored hashed and every user gets an own key salt")
    void storesHashedPasswordAndSalt() {
        when(repository.existsByUsernameIgnoreCase(anyString())).thenReturn(false);
        when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = service.register("lesya", "correct horse battery");

        assertThat(user.getPasswordHash()).isNotEqualTo("correct horse battery").startsWith("$2");
        assertThat(passwordEncoder.matches("correct horse battery", user.getPasswordHash())).isTrue();
        assertThat(user.getKeySalt()).hasSize(16);
    }

    @Test
    @DisplayName("Two users with the same password get different salts")
    void generatesDifferentSaltsPerUser() {
        when(repository.existsByUsernameIgnoreCase(anyString())).thenReturn(false);
        when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User first = service.register("first", "the same password");
        User second = service.register("second", "the same password");

        assertThat(first.getKeySalt()).isNotEqualTo(second.getKeySalt());
        assertThat(first.getPasswordHash()).isNotEqualTo(second.getPasswordHash());
    }

    @Test
    @DisplayName("User name is trimmed before it is stored")
    void trimsUserName() {
        when(repository.existsByUsernameIgnoreCase(anyString())).thenReturn(false);
        when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.register("  lesya  ", "correct horse battery").getUsername()).isEqualTo("lesya");
    }

    @Test
    @DisplayName("Taken user name is rejected")
    void rejectsTakenUserName() {
        when(repository.existsByUsernameIgnoreCase("lesya")).thenReturn(true);

        assertThatThrownBy(() -> service.register("lesya", "correct horse battery"))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessage("User 'lesya' already exists");

        verify(repository, never()).save(any());
    }
}
