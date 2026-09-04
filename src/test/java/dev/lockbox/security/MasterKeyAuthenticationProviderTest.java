package dev.lockbox.security;

import dev.lockbox.crypto.CryptoProperties;
import dev.lockbox.crypto.KeyDerivation;
import dev.lockbox.user.User;
import dev.lockbox.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MasterKeyAuthenticationProviderTest {

    private static final String PASSWORD = "correct horse battery";

    private final UserRepository repository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final KeyDerivation keyDerivation = new KeyDerivation(new CryptoProperties(65536, 3, 1, 16));
    private final MasterKeyStore masterKeyStore = new MasterKeyStore();

    private MasterKeyAuthenticationProvider provider;
    private User user;

    @BeforeEach
    void setUp() {
        provider = new MasterKeyAuthenticationProvider(repository, passwordEncoder, keyDerivation, masterKeyStore);
        user = new User();
        user.setUsername("lesya");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setKeySalt(keyDerivation.newSalt());
    }

    @Test
    @DisplayName("Correct master password unlocks the vault for this session")
    void unlocksVaultOnSuccess() {
        when(repository.findByUsernameIgnoreCase("lesya")).thenReturn(Optional.of(user));

        Authentication authentication = provider.authenticate(token("lesya", PASSWORD));

        assertThat(authentication.getName()).isEqualTo("lesya");
        assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
        assertThat(masterKeyStore.isUnlocked()).isTrue();
    }

    @Test
    @DisplayName("Master key is derived from the salt of that very user")
    void derivesKeyFromUserSalt() {
        when(repository.findByUsernameIgnoreCase("lesya")).thenReturn(Optional.of(user));

        provider.authenticate(token("lesya", PASSWORD));

        assertThat(masterKeyStore.requireMasterKey().getEncoded())
                .isEqualTo(keyDerivation.deriveMasterKey(PASSWORD.toCharArray(), user.getKeySalt()).getEncoded());
    }

    @Test
    @DisplayName("Wrong password leaves the vault locked")
    void keepsVaultLockedOnWrongPassword() {
        when(repository.findByUsernameIgnoreCase("lesya")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> provider.authenticate(token("lesya", "wrong password")))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(masterKeyStore.isUnlocked()).isFalse();
    }

    @Test
    @DisplayName("Unknown user and wrong password report the same message, so accounts cannot be probed")
    void doesNotRevealWhetherUserExists() {
        when(repository.findByUsernameIgnoreCase(anyString())).thenReturn(Optional.empty());
        String unknownUserMessage = messageOf("ghost", PASSWORD);

        when(repository.findByUsernameIgnoreCase("lesya")).thenReturn(Optional.of(user));
        String wrongPasswordMessage = messageOf("lesya", "wrong password");

        assertThat(unknownUserMessage).isEqualTo(wrongPasswordMessage);
        assertThat(masterKeyStore.isUnlocked()).isFalse();
    }

    private String messageOf(String username, String password) {
        try {
            provider.authenticate(token(username, password));
            throw new AssertionError("Authentication was expected to fail");
        } catch (BadCredentialsException e) {
            return e.getMessage();
        }
    }

    private Authentication token(String username, String password) {
        return new UsernamePasswordAuthenticationToken(username, password);
    }
}
